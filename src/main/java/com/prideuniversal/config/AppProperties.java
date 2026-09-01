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
        Cors cors
) {

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

    public record Sms(String provider, Msg91 msg91) {
        public record Msg91(String authKey, String templateId, String senderId) {}
    }

    public record Storage(String provider, Local local, int maxFileSizeMb) {
        public record Local(String basePath) {}
    }

    public record Cors(List<String> allowedOrigins) {}
}
