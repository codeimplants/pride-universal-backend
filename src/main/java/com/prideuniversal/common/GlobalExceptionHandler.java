package com.prideuniversal.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiError> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.of(ex.getCode(), ex.getMessage()));
    }

    /** Bean-validation failures on {@code @Valid} request bodies. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(ApiError.validation("Request validation failed", fields));
    }

    /**
     * Someone else changed the row between our read and our write.
     * Surfaced as 409 so the client can re-fetch and retry rather than
     * silently clobbering the other edit.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError.of(
                "concurrent_modification",
                "This record was changed by someone else. Reload and try again."));
    }

    /**
     * Unique / check / FK constraint violations. The database is the last line
     * of defence, so these are expected, not exceptional - e.g. generating
     * maintenance bills twice for the same month.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleIntegrity(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        log.warn("Constraint violation: {}", detail);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiError.of("constraint_violation", friendlyConstraintMessage(detail)));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiError.of("forbidden", "You do not have permission to do that."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiError.of("unauthorized", "Authentication required."));
    }

    /**
     * No controller matched the URL. Spring raises this as
     * {@code NoResourceFoundException}; without an explicit handler the
     * catch-all below turned every 404 into a 500, which tells the client the
     * server is broken when in fact the path was simply wrong.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("not_found", "No such endpoint."));
    }

    /**
     * Body was absent, truncated or not valid JSON. That is the caller's
     * mistake, so it is a 400 - not a 500. The parser's message is deliberately
     * not echoed back: it quotes the offending input, which may contain
     * credentials the client mis-sent.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        log.debug("Malformed request body: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiError.of("malformed_request", "Request body is missing or not valid JSON."));
    }

    /**
     * Last resort.
     *
     * <p>Ordering note: a handler for {@code Exception} matches literally
     * everything, including the typed exceptions Spring MVC raises for ordinary
     * client mistakes (wrong path, wrong HTTP method, unsupported content
     * type). Left unqualified it reports all of them as 500 and logs each with
     * a stack trace, which both misleads the client and buries genuine faults
     * in noise.
     *
     * <p>So before assuming a server fault we ask the exception itself: Spring
     * 6's {@link ErrorResponse} interface is implemented by those MVC
     * exceptions and already carries the correct status. Honouring it here
     * keeps future cases (405, 415, ...) right without having to enumerate
     * them, and reserves ERROR-level logging for things that really are bugs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        if (ex instanceof ErrorResponse er) {
            HttpStatus status = HttpStatus.valueOf(er.getStatusCode().value());
            log.warn("{} on {} {}: {}",
                    status, request.getMethod(), request.getRequestURI(), ex.getMessage());
            return ResponseEntity.status(status)
                    .body(ApiError.of(status.name().toLowerCase(), status.getReasonPhrase()));
        }
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiError.of(
                "internal_error",
                "Something went wrong. Please try again."));
    }

    /** Translate the more common Postgres constraint names into plain English. */
    private String friendlyConstraintMessage(String detail) {
        if (detail == null) {
            return "That change conflicts with existing data.";
        }
        if (detail.contains("uq_bill_flat_period")) {
            return "A maintenance bill already exists for this flat and month.";
        }
        if (detail.contains("uq_flat_wing_number")) {
            return "That flat number already exists in this wing.";
        }
        if (detail.contains("app_user_mobile_key")) {
            return "That mobile number is already registered.";
        }
        if (detail.contains("ck_bill_paid_consistent")) {
            return "A paid bill must have a payment date and payment mode.";
        }
        if (detail.contains("ck_user_resident_flat")) {
            return "A resident must be linked to a flat.";
        }
        return "That change conflicts with existing data.";
    }
}
