package com.prideuniversal.config;

import com.prideuniversal.auth.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless, token-based security.
 *
 * <p>Contrast with what this replaces: the base44 app set
 * {@code requiresAuth: false} and derived the caller's role from
 * {@code sessionStorage}, so authorisation was a client-side suggestion.
 * Here every request carries a signed JWT, the role is read from that token,
 * and endpoint rules are enforced before a controller method is entered.
 */
@Configuration
@EnableMethodSecurity   // turns on @PreAuthorize on service/controller methods
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final AppProperties props;
    private final SecurityErrorResponder errorResponder;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          AppProperties props,
                          SecurityErrorResponder errorResponder) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.props = props;
        this.errorResponder = errorResponder;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // No server-side session, so there is no session to fixate and no
            // cookie to forge - CSRF protection is not applicable to a pure
            // bearer-token API. (It WOULD be needed if we used cookie auth.)
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // --- Public. Listed individually rather than as
                //     /api/auth/** because /api/auth/me is NOT public. ---
                .requestMatchers("/api/auth/otp/send", "/api/auth/otp/verify").permitAll()
                .requestMatchers("/api/auth/refresh", "/api/auth/logout").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // --- Committee-only areas, enforced at the URL layer.
                //     Finer-grained rules live on the service methods via
                //     @PreAuthorize, so a controller can never be the only
                //     thing standing between a resident and the ledger. ---
                .requestMatchers("/api/settings/**")
                    .hasAnyRole("ADMIN", "CHAIRMAN")
                .requestMatchers("/api/transactions/**")
                    .hasAnyRole("ADMIN", "CHAIRMAN", "SECRETARY", "TREASURER")
                .requestMatchers("/actuator/**")
                    .hasRole("ADMIN")

                // --- Everything else needs a valid token ---
                .anyRequest().authenticated()
            )
            // Without this, a request with no token gets a bare 403. Spring
            // Security fails requests inside the filter chain, so
            // GlobalExceptionHandler never sees them - these two collaborators
            // are what turn them into 401/403 with a normal ApiError body.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(errorResponder)
                .accessDeniedHandler(errorResponder))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Used to hash OTP codes and refresh tokens before they touch the database.
     * Strength 10 is the Spring default; OTP verification happens once per
     * login so the cost is not on a hot path.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.cors().allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
