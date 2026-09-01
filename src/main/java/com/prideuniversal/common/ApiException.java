package com.prideuniversal.common;

import org.springframework.http.HttpStatus;

/**
 * Base class for errors we deliberately surface to the client with a chosen
 * status code. Anything else escaping a controller becomes a 500 and is logged
 * with a stack trace but never leaked to the caller.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    // --- Common shapes ------------------------------------------------------

    public static ApiException notFound(String what) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found");
    }

    public static ApiException notFound(String what, Object id) {
        return new ApiException(HttpStatus.NOT_FOUND, "not_found", what + " not found: " + id);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "bad_request", message);
    }

    public static ApiException forbidden(String message) {
        return new ApiException(HttpStatus.FORBIDDEN, "forbidden", message);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", message);
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "conflict", message);
    }

    public static ApiException tooManyRequests(String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "rate_limited", message);
    }
}
