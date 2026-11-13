package com.prideuniversal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Typed binding for the {@code app.*} block in application.yml.
 *
 * <p>Records give constructor binding for free, which means these values are
 * immutable and validated at startup rather than being read as loose strings
 * via {@code @Value} scattered across the codebase. A typo in a property name
 * fails the context on boot instead of surfacing as a null at 2am.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Otp otp,
        Sms sms,
        Storage storage,
        Cors cors,
        Admin admin
) {

    /** Nexus (the Code Implants admin console) authenticates with this on /api/admin/**. */
    public record Admin(String serviceToken) {}

    public record Jwt(
            String secret,
            Duration accessTokenTtl,
            Duration refreshTokenTtl,
            String issuer
    ) {}

    public record Otp(
            int length,
            Duration ttl,
            int maxAttempts,
            Duration resendCooldown,
            boolean devMode
    ) {}

    /**
     * {@code provider}: {@code console} (local; logs the code) or {@code messaging}
     * (Code Implants Messaging; every deployed environment).
     */
    public record Sms(String provider, Messaging messaging) {
        public record Messaging(String baseUrl, String apiKey, String fixedCode) {}
    }

    public record Storage(String provider, Local local, int maxFileSizeMb) {
        public record Local(String basePath) {}
    }

    public record Cors(List<String> allowedOrigins) {}
}
