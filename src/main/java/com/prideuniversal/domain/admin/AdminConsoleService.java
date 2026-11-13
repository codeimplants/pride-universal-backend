package com.prideuniversal.domain.admin;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.domain.user.AccountDeletion;
import com.prideuniversal.domain.user.User;
import com.prideuniversal.domain.user.UserRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What Nexus reads and does through {@code /api/admin/**}.
 *
 * <p>Field names follow Nexus's federation adapter ({@code AppAdminService.toProfileMap}
 * and {@code toDeletedIdSet} in nexus-backend): users carry {@code id}, {@code name},
 * {@code phone} and {@code registrationDate}; the deleted log carries
 * {@code originalUserId}. The ids are the ones the app reports to Nexus analytics
 * when it identifies a signed-in user, so Nexus can put names to its engagement rows.
 *
 * <p>Nexus reads <b>camelCase</b> keys, while this API writes snake_case everywhere
 * else, so these records override the naming. Under snake_case the deleted log's
 * {@code original_user_id} would match none of the keys Nexus looks for, and its
 * purge would quietly remove nothing.
 */
@Service
public class AdminConsoleService {

    private final UserRepository userRepository;
    private final AccountDeletion accountDeletion;
    private final JdbcTemplate jdbc;

    public AdminConsoleService(UserRepository userRepository, AccountDeletion accountDeletion, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.accountDeletion = accountDeletion;
        this.jdbc = jdbc;
    }

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record ConsoleUser(
            UUID id,
            String name,
            String phone,
            Instant registrationDate,
            String role,
            String wing,
            String flatNumber,
            boolean active,
            Instant lastLoginAt
    ) {
        static ConsoleUser from(User u) {
            var flat = u.getFlat();
            return new ConsoleUser(u.getId(), u.getDisplayName(), u.getMobile(), u.getCreatedDate(),
                    u.getRole().getCode(), flat != null ? flat.getWing() : null,
                    flat != null ? flat.getFlatNumber() : null, u.isActive(), u.getLastLoginAt());
        }
    }

    @JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
    public record DeletedUser(UUID originalUserId, String role, String deletedBy, Instant deletedAt) {}

    @Transactional(readOnly = true)
    public List<ConsoleUser> users() {
        return userRepository.findAllByOrderByDisplayNameAsc().stream().map(ConsoleUser::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DeletedUser> deletedUsers() {
        return jdbc.query("""
                        select user_id, role, deleted_by, deleted_at from deleted_account
                        order by deleted_at desc limit 5000
                        """,
                (rs, i) -> new DeletedUser(rs.getObject("user_id", UUID.class), rs.getString("role"),
                        rs.getString("deleted_by"), rs.getTimestamp("deleted_at").toInstant()));
    }

    /** A missing account is a 404, which Nexus reads as "already gone". */
    @Transactional
    public void deleteUser(UUID id) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User", id));
        accountDeletion.delete(user, AccountDeletion.BY_ADMIN_CONSOLE);
    }

    /** Society totals, plus sign-ups, complaints and visitors per IST day for the last {@code days} days. */
    @Transactional(readOnly = true)
    public Map<String, Object> stats(int days) {
        if (days < 1 || days > 366) {
            throw ApiException.badRequest("days must be 1 to 366");
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("flats", count("select count(*) from flat where is_active"));
        totals.put("users", count("select count(*) from app_user where is_active"));
        totals.put("residents", count("select count(*) from app_user where is_active and role = 'resident'"));
        totals.put("committee", count("select count(*) from app_user where is_active and role in "
                + "('admin','chairman','secretary','treasurer','committee_member')"));
        totals.put("security", count("select count(*) from app_user where is_active and role = 'security'"));
        totals.put("activeUsers30d", count("select count(*) from app_user where last_login_at > now() - interval '30 days'"));
        totals.put("newUsers7d", count("select count(*) from app_user where created_date > now() - interval '7 days'"));
        totals.put("newUsers30d", count("select count(*) from app_user where created_date > now() - interval '30 days'"));
        totals.put("openComplaints", count("select count(*) from complaint where status in ('open','in_progress')"));
        totals.put("billsDue", count("select count(*) from maintenance_bill where status in ('pending','overdue')"));
        totals.put("amountDue", jdbc.queryForObject("select coalesce(sum(amount + late_fee), 0) from maintenance_bill "
                + "where status in ('pending','overdue')", BigDecimal.class));
        totals.put("notices", count("select count(*) from notice where is_published"));
        totals.put("deletedAccounts", count("select count(*) from deleted_account"));

        List<Map<String, Object>> daily = jdbc.query("""
                        with d as (
                          select generate_series(current_date - (? - 1), current_date, interval '1 day')::date as day
                        )
                        select d.day,
                          (select count(*) from app_user u where (u.created_date at time zone 'Asia/Kolkata')::date = d.day) as signups,
                          (select count(*) from complaint c where (c.created_date at time zone 'Asia/Kolkata')::date = d.day) as complaints,
                          (select count(*) from visitor v where (v.created_date at time zone 'Asia/Kolkata')::date = d.day) as visitors
                        from d order by d.day
                        """,
                (rs, i) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("day", rs.getObject("day", LocalDate.class));
                    row.put("signups", rs.getLong("signups"));
                    row.put("complaints", rs.getLong("complaints"));
                    row.put("visitors", rs.getLong("visitors"));
                    return row;
                }, days);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totals", totals);
        body.put("daily", daily);
        return body;
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }
}
