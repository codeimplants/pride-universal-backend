package com.prideuniversal.auth.dto;

import com.prideuniversal.common.Role;
import com.prideuniversal.domain.user.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * Request and response shapes for the auth endpoints.
 *
 * <p>Records, not entities. Exposing a JPA entity directly on the wire is a
 * habit worth avoiding: it couples the API to the schema, drags lazy
 * associations into serialisation (triggering N+1 queries or
 * LazyInitializationException), and makes it far too easy to accept a client
 * value for a field like {@code role} that the client must never control.
 *
 * <p>Jackson is configured for snake_case, so {@code accessToken} is
 * {@code access_token} on the wire.
 */
public final class AuthDtos {

    private AuthDtos() {}

    // --- Requests -----------------------------------------------------------

    public record SendOtpRequest(
            @NotBlank(message = "Mobile number is required")
            @Pattern(regexp = "^[6-9][0-9]{9}$",
                     message = "Enter a valid 10-digit Indian mobile number")
            String mobile
    ) {}

    public record VerifyOtpRequest(
            @NotBlank(message = "Mobile number is required")
            @Pattern(regexp = "^[6-9][0-9]{9}$",
                     message = "Enter a valid 10-digit Indian mobile number")
            String mobile,

            @NotBlank(message = "OTP is required")
            @Pattern(regexp = "^[0-9]{4,8}$", message = "Enter the code you received")
            String otp
    ) {}

    public record RefreshRequest(
            @NotBlank(message = "Refresh token is required")
            String refreshToken
    ) {}

    // --- Responses ----------------------------------------------------------

    /**
     * @param devOtp populated only when app.otp.dev-mode is true, so the app
     *               can be exercised without a live SMS account. Null in prod.
     */
    public record SendOtpResponse(
            boolean success,
            String message,
            String devOtp
    ) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            long expiresIn,
            UserResponse user
    ) {}

    public record UserResponse(
            UUID id,
            String mobile,
            String displayName,
            Role role,
            UUID flatId,
            String flatNumber,
            String wing,
            boolean isManagement
    ) {
        public static UserResponse from(User user) {
            var flat = user.getFlat();
            return new UserResponse(
                    user.getId(),
                    user.getMobile(),
                    user.getDisplayName(),
                    user.getRole(),
                    flat != null ? flat.getId() : null,
                    flat != null ? flat.getFlatNumber() : null,
                    flat != null ? flat.getWing() : null,
                    user.getRole().isManagement());
        }
    }
}
