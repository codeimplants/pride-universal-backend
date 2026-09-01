package com.prideuniversal.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Reads the bearer token off each request and, if it is valid, populates the
 * SecurityContext with an {@link AuthPrincipal}.
 *
 * <p>Extends {@code OncePerRequestFilter} rather than implementing {@code Filter}
 * directly: without that guarantee the filter runs again on internal forwards
 * and error dispatches, re-doing the parse for no reason.
 *
 * <p>This filter never rejects a request. It either establishes an identity or
 * leaves the context anonymous, and lets the authorisation rules in
 * {@code SecurityConfig} decide what that means for the endpoint being called.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
            throws ServletException, IOException {

        String token = extractToken(request);

        // Only attempt authentication if nothing upstream already did.
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            AuthPrincipal principal = jwtService.parse(token);
            if (principal != null) {
                var authorities = List.of(new SimpleGrantedAuthority(principal.role().authority()));
                var authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, authorities);
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        chain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String value = header.substring(PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }

    /**
     * Skips only the endpoints that can never carry a token.
     *
     * <p>Note what is deliberately NOT skipped: {@code /api/auth/**}. The login
     * endpoints are public, but {@code /api/auth/me} is not, and skipping the
     * whole prefix would leave it with no principal to read - so it would
     * reject every caller including valid ones. Public and unauthenticated are
     * not the same thing: this filter establishes identity when a token is
     * present and stays out of the way when one is not, and
     * {@code SecurityConfig} decides which endpoints require it.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
