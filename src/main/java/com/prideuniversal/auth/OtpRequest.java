package com.prideuniversal.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One issued OTP.
 *
 * <p>The code is stored only as a BCrypt hash. If this table leaks, the codes
 * in it are not usable - and they have a five-minute lifetime anyway. The old
 * implementation compared against a hardcoded {@code '000000'} in source.
 *
 * <p>Does not extend BaseEntity: rows here are short-lived append-only records,
 * not domain objects, so optimistic locking and an updated_date would be noise.
 */
@Entity
@Table(name = "otp_request")
@Getter
@Setter
public class OtpRequest {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 10)
    private String mobile;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate = Instant.now();

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isUsable() {
        return !isConsumed() && !isExpired();
    }
}
