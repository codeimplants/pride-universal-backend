package com.prideuniversal.domain.user;

import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.common.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one way an account is deleted, whether its owner asks in the app or Nexus
 * purges it.
 *
 * <p>The user row goes, and with it the refresh tokens ({@code ON DELETE CASCADE}).
 * Records the society keeps — flats, bills, complaints, notices — lose only the
 * link to the account ({@code ON DELETE SET NULL}). The deletion is logged without
 * personal data so Nexus can drop its telemetry for the same id.
 *
 * <p>The last active admin can never be deleted: that would lock the society out.
 */
@Component
public class AccountDeletion {

    public static final String BY_SELF = "self";
    public static final String BY_ADMIN_CONSOLE = "admin_console";

    private static final Logger log = LoggerFactory.getLogger(AccountDeletion.class);

    private final UserRepository userRepository;
    private final JdbcTemplate jdbc;

    public AccountDeletion(UserRepository userRepository, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void delete(User user, String deletedBy) {
        if (user.getRole() == Role.ADMIN && user.isActive()
                && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict(
                    "This is the only admin account. Make someone else admin before deleting it.");
        }
        jdbc.update("insert into deleted_account (user_id, role, deleted_by) values (?, ?, ?)",
                user.getId(), user.getRole().getCode(), deletedBy);
        userRepository.delete(user);
        userRepository.flush();
        log.info("Account {} deleted ({})", Text.mask(user.getMobile()), deletedBy);
    }
}
