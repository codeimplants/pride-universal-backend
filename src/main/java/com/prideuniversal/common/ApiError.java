package com.prideuniversal.common;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body. Jackson is configured for snake_case, so this serialises
 * as {@code {"code":..., "message":..., "field_errors":..., "timestamp":...}}.
 */
public record ApiError(
        String code,
        String message,
        Map<String, String> fieldErrors,
        Instant timestamp
) {
    public static ApiError of(String code, String message) {
        return new ApiError(code, message, null, Instant.now());
    }

    public static ApiError validation(String message, Map<String, String> fieldErrors) {
        return new ApiError("validation_failed", message, fieldErrors, Instant.now());
    }
}
