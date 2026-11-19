package com.prideuniversal.domain.gate;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Predicate;

/** Gate codes, and the society's calendar day. */
final class Codes {

    static final ZoneId SOCIETY_ZONE = ZoneId.of("Asia/Kolkata");
    private static final SecureRandom RANDOM = new SecureRandom();

    private Codes() {}

    /** A random 6-digit code nobody else holds. */
    static String fresh(Predicate<String> taken) {
        for (int i = 0; i < 50; i++) {
            String c = String.format("%06d", RANDOM.nextInt(1_000_000));
            if (!taken.test(c)) {
                return c;
            }
        }
        throw new IllegalStateException("Could not find a free gate code");
    }

    static LocalDate today() {
        return LocalDate.now(SOCIETY_ZONE);
    }
}
