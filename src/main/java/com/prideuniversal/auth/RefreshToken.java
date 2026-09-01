package com.prideuniversal.auth;

import com.prideuniversal.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A long-lived refresh token, stored hashed.
 *
 * <p>Why have these at all: access tokens are deliberately short-lived (12h) so
 * that a revoked or demoted user loses access quickly, since a signed JWT
 * cannot be un-signed. The refresh token is the part we CAN revoke, because it
 * lives in the database - logging out deletes the row and the session is
 * genuinely over.
 */
@Entity
@Table(name = "refresh_token")
@Getter
@Setter
public class RefreshToken {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate = Instant.now();

    public boolean isActive() {
        return revokedAt == null && Instant.now().isBefore(expiresAt);
    }
}
