package com.prideuniversal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Authenticates Nexus, the Code Implants admin console, by a static bearer token
 * ({@code ADMIN_SERVICE_TOKEN}), on {@code /api/admin/**} only. Same as RestoBill's.
 *
 * <p>Both sides are hashed before the constant-time compare, so neither the token's
 * content nor its length leaks through timing. With no token configured nothing
 * authenticates, and the chain answers 401 to everything.
 *
 * <p>Not a {@code @Component}: Spring Boot would also register it as a servlet filter
 * for every path. It is added to the admin chain alone in {@link SecurityConfig}.
 */
class ServiceTokenFilter extends OncePerRequestFilter {

    static final String AUTHORITY = "ADMIN_CONSOLE";
    private static final String BEARER = "Bearer ";

    private final byte[] expected;

    ServiceTokenFilter(String token) {
        this.expected = token == null || token.isBlank() ? null : sha256(token.strip());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (expected != null && header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())
                && MessageDigest.isEqual(expected, sha256(header.substring(BEARER.length()).strip()))) {
            var auth = UsernamePasswordAuthenticationToken.authenticated(
                    "admin-console", null, List.of(new SimpleGrantedAuthority(AUTHORITY)));
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(auth);
            SecurityContextHolder.setContext(context);
        }
        chain.doFilter(request, response);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
