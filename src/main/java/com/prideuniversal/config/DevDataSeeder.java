package com.prideuniversal.config;

import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.user.User;
import com.prideuniversal.domain.settings.WingSettings;
import com.prideuniversal.domain.settings.WingSettingsRepository;
import com.prideuniversal.domain.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds a usable dataset for local development.
 *
 * <p>{@code @Profile("dev")} means this bean does not exist in any other
 * profile - it cannot run in production even by accident. That is the reason
 * this is a seeder bean rather than a Flyway migration: Flyway runs everywhere,
 * and test fixtures do not belong in a production database.
 *
 * <p>Idempotent: if flats already exist it does nothing, so restarting the app
 * does not duplicate data.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final FlatRepository flatRepository;
    private final UserRepository userRepository;
    private final WingSettingsRepository wingSettingsRepository;
    private final String adminMobile;

    public DevDataSeeder(FlatRepository flatRepository,
                         UserRepository userRepository,
                         WingSettingsRepository wingSettingsRepository,
                         @Value("${app.dev.admin-mobile:9850929698}") String adminMobile) {
        this.flatRepository = flatRepository;
        this.userRepository = userRepository;
        this.wingSettingsRepository = wingSettingsRepository;
        this.adminMobile = adminMobile;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Checked per wing and before the flats guard below, so a database
        // seeded before billing existed still gets its settings rows.
        seedWingSettings();

        if (flatRepository.count() > 0) {
            log.info("Dev data already present, skipping seed.");
            return;
        }

        log.info("Seeding development data...");

        List<Flat> flats = List.of(
                flat("A", "101", 1, "Ramesh Kulkarni",  "9876543210", false, null, null),
                flat("A", "102", 1, "Sunita Deshmukh",  "9876543211", true,  "Amit Patil",   "9876543212"),
                flat("A", "201", 2, "Vijay Joshi",      "9876543213", false, null, null),
                flat("A", "202", 2, "Meera Shah",       "9876543214", false, null, null),
                flat("B", "101", 1, "Anil Gaikwad",     "9876543215", false, null, null),
                flat("B", "102", 1, "Priya Nair",       "9876543216", true,  "Rohit Verma",  "9876543217"),
                flat("B", "201", 2, "Sanjay More",      "9876543218", false, null, null),
                flat("B", "202", 2, "Kavita Rane",      "9876543219", false, null, null)
        );
        flatRepository.saveAll(flats);

        // The bootstrap admin. Every other committee role is assigned by an
        // admin through the API - roles are never self-assigned at login.
        if (!userRepository.existsByMobile(adminMobile)) {
            User admin = new User();
            admin.setMobile(adminMobile);
            admin.setDisplayName("Society Admin");
            admin.setRole(Role.ADMIN);
            admin.setActive(true);
            userRepository.save(admin);
        }

        log.info("""

                ================= DEV DATA SEEDED =================
                  Flats     : {}
                  Admin     : {} (role=admin)
                  Resident  : 9876543210 (Ramesh Kulkarni, A-101)
                  Tenant    : 9876543212 (Amit Patil, A-102)

                  Log in via POST /api/auth/otp/send - the OTP is
                  printed in this console.
                ===================================================
                """, flats.size(), adminMobile);
    }

    /**
     * Billing rates per wing. Without these, bill generation refuses to run -
     * deliberately, since billing everyone zero would look like success.
     *
     * <p>Note this configures the EXISTING rows rather than creating them.
     * V1__initial_schema.sql already inserts one wing_settings row per wing
     * with every amount defaulted to zero, so an "exists?" check would always
     * be true and this would never set an amount.
     */
    private void seedWingSettings() {
        for (String wing : List.of("A", "B")) {
            WingSettings settings = wingSettingsRepository.findByWing(wing)
                    .orElseGet(() -> {
                        WingSettings created = new WingSettings();
                        created.setWing(wing);
                        return created;
                    });
            if (settings.monthlyTotal().signum() > 0) {
                continue;               // a real amount is already configured
            }
            settings.setMonthlyMaintenance(new BigDecimal("2000.00"));
            settings.setSinkingFundAmount(new BigDecimal("500.00"));
            settings.setWaterCharges(new BigDecimal("250.00"));
            settings.setLateFee(new BigDecimal("100.00"));
            settings.setMaintenanceDueDay(10);
            settings.setSocietyName("Pride Universal Co-operative Housing Society");
            wingSettingsRepository.save(settings);
            log.info("Seeded billing settings for wing {}", wing);
        }
    }

    private Flat flat(String wing, String number, int floor, String owner, String ownerContact,
                      boolean isTenant, String tenantName, String tenantContact) {
        Flat f = new Flat();
        f.setWing(wing);
        f.setFlatNumber(number);
        f.setFloor(floor);
        f.setOwnerName(owner);
        f.setOwnerContact(ownerContact);
        f.setTenant(isTenant);
        f.setTenantName(tenantName);
        f.setTenantContact(tenantContact);
        f.setStaying(!isTenant);
        f.setActive(true);
        return f;
    }
}
