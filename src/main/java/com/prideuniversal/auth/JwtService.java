package com.prideuniversal.auth;

import com.prideuniversal.common.Role;
import com.prideuniversal.config.AppProperties;
import com.prideuniversal.domain.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

/**
 * Issues and verifies the signed access tokens that carry a caller's identity
 * and role.
 *
 * <p>Signed, not encrypted: the claims are readable by anyone holding the
 * token, so nothing secret goes in them. What the signature guarantees is that
 * the claims have not been <em>altered</em> - which is precisely the property
 * the old sessionStorage approach lacked.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_MOBILE = "mobile";
    private static final String CLAIM_FLAT_ID = "flat_id";
    private static final String CLAIM_NAME = "name";

    private final SecretKey key;
    private final AppProperties.Jwt config;
    private final SecureRandom random = new SecureRandom();

    public JwtService(AppProperties props) {
        this.config = props.jwt();
        byte[] secret = config.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            // HS256 needs >= 256 bits of key material. Failing loudly at
            // startup beats discovering this when the first login is attempted.
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes; got " + secret.length);
        }
        this.key = Keys.hmacShaKeyFor(secret);
    }

    /** Mints an access token for a freshly authenticated user. */
    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(config.accessTokenTtl());

        return Jwts.builder()
                .subject(user.getId().toString())
                .issuer(config.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .claim(CLAIM_ROLE, user.getRole().getCode())
                .claim(CLAIM_MOBILE, user.getMobile())
                .claim(CLAIM_NAME, user.getDisplayName())
                .claim(CLAIM_FLAT_ID, user.getFlat() != null ? user.getFlat().getId().toString() : null)
                .signWith(key)
                .compact();
    }

    /**
     * Verifies signature, issuer and expiry, then rebuilds the principal.
     * Returns null for any invalid token - callers treat that as anonymous.
     */
    public AuthPrincipal parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(config.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String flatId = claims.get(CLAIM_FLAT_ID, String.class);

            return new AuthPrincipal(
                    UUID.fromString(claims.getSubject()),
                    claims.get(CLAIM_MOBILE, String.class),
                    Role.fromCode(claims.get(CLAIM_ROLE, String.class)),
                    flatId != null ? UUID.fromString(flatId) : null,
                    claims.get(CLAIM_NAME, String.class));

        } catch (JwtException | IllegalArgumentException ex) {
            // Expired, tampered, wrong issuer, malformed - all the same to us.
            log.debug("Rejected JWT: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * A refresh token is opaque random material, not a JWT. There is nothing to
     * read from it - its only job is to be presented back and matched against
     * the hash we stored, which is what makes revocation possible.
     */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[48];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(config.refreshTokenTtl());
    }

    public long accessTokenSeconds() {
        return config.accessTokenTtl().toSeconds();
    }
}
