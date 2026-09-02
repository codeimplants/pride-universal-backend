package com.prideuniversal.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prideuniversal.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Produces the API's standard {@link ApiError} body for security failures.
 *
 * <p><b>Why this is needed even though GlobalExceptionHandler already handles
 * these exceptions.</b> {@code @RestControllerAdvice} only sees exceptions that
 * escape the {@code DispatcherServlet}. Spring Security rejects a request in
 * the servlet <em>filter chain</em>, which runs before the DispatcherServlet is
 * ever entered, so those handlers are unreachable for auth failures.
 * {@code ExceptionTranslationFilter} handles them instead, and without these
 * two collaborators it falls back to an empty body.
 *
 * <p><b>Why the status code matters.</b> Out of the box an anonymous request
 * got {@code 403 Forbidden}. The distinction the client depends on is:
 * <ul>
 *   <li>{@code 401} — "I don't know who you are." Token missing or expired;
 *       the correct client reaction is to refresh the token, then retry.</li>
 *   <li>{@code 403} — "I know who you are, and the answer is no." Retrying
 *       with a fresh token changes nothing; show the user a message.</li>
 * </ul>
 * Collapsing both into 403 would make the mobile app tell a resident whose
 * token merely expired that they are not allowed in, instead of silently
 * refreshing. {@code ExceptionTranslationFilter} picks between the two for us:
 * an anonymous caller is routed to {@link #commence}, an authenticated one to
 * {@link #handle}.
 */
@Component
public class SecurityErrorResponder implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** No credentials, or credentials we could not validate. */
    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required.");
    }

    /** Valid credentials, insufficient privileges. */
    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        write(response, HttpStatus.FORBIDDEN, "forbidden", "You do not have permission to do that.");
    }

    /**
     * Deliberately says nothing about <em>why</em> the request failed. Telling
     * an unauthenticated caller the difference between "no such user" and
     * "wrong token" hands them an account-enumeration oracle.
     */
    private void write(HttpServletResponse response, HttpStatus status, String code, String message)
            throws IOException {
        // The injected ObjectMapper is Spring Boot's configured one, so this
        // body gets the same snake_case naming as every other response.
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ApiError.of(code, message));
    }
}
