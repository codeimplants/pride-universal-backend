package com.prideuniversal.auth;

import com.prideuniversal.common.ApiException;
import com.prideuniversal.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Issues, rotates and revokes the token pair.
 *
 * <p>Refresh tokens are hashed with SHA-256 rather than BCrypt. That is a
 * deliberate difference from the OTP: BCrypt is intentionally slow to make
 * brute-forcing a 6-digit code infeasible, but a refresh token is 48 bytes of
 * cryptographic randomness that cannot be brute-forced at all. It also has to
 * be looked up by value on every refresh, and BCrypt's per-row salt would
 * force a full table scan. SHA-256 hashes deterministically, so the lookup is
 * a single indexed query.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public TokenService(RefreshTokenRepository refreshTokenRepository, JwtService jwtService) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    /** Mints a fresh access + refresh pair for a just-authenticated user. */
    @Transactional
    public TokenPair issue(User user, String userAgent) {
        String refreshValue = jwtService.generateRefreshTokenValue();

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(refreshValue));
        token.setExpiresAt(jwtService.refreshTokenExpiry());
        token.setUserAgent(truncate(userAgent));
        refreshTokenRepository.save(token);

        return new TokenPair(
                jwtService.createAccessToken(user),
                refreshValue,
                jwtService.accessTokenSeconds());
    }

    /**
     * Exchanges a refresh token for a new pair, rotating the refresh token.
     *
     * <p>Rotation matters: each refresh token is single-use, so a stolen one is
     * only usable until the legitimate client next refreshes. Without it, a
     * leaked refresh token is valid for its full 30-day life.
     */
    @Transactional
    public RefreshResult refresh(String refreshValue, String userAgent) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash(refreshValue))
                .orElseThrow(() -> ApiException.unauthorized("Invalid session. Please log in again."));

        if (!stored.isActive()) {
            throw ApiException.unauthorized("Your session has expired. Please log in again.");
        }

        User user = stored.getUser();
        if (!user.isActive()) {
            throw ApiException.forbidden("This account has been deactivated.");
        }

        // Retire the presented token before issuing its replacement.
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        // The user is returned alongside the tokens because the refresh
        // endpoint is unauthenticated - there is no SecurityContext to read
        // the caller from, and the refresh token itself is the only proof of
        // identity. Re-reading here also means a role change made by an admin
        // takes effect on the next refresh rather than at access-token expiry.
        return new RefreshResult(issue(user, userAgent), user);
    }

    /** Ends one session. */
    @Transactional
    public void revoke(String refreshValue) {
        refreshTokenRepository.findByTokenHash(hash(refreshValue)).ifPresent(token -> {
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
            log.info("Refresh token revoked for user {}", token.getUser().getId());
        });
    }

    /** Ends every session for a user - used when an account is deactivated. */
    @Transactional
    public int revokeAll(User user) {
        return refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JDK spec; this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String truncate(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return userAgent.length() > 255 ? userAgent.substring(0, 255) : userAgent;
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}

    public record RefreshResult(TokenPair tokens, User user) {}
}
