package com.prideuniversal.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
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
