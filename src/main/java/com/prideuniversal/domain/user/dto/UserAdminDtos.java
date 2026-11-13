package com.prideuniversal.domain.user.dto;

import com.prideuniversal.common.Role;
import com.prideuniversal.domain.user.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class UserAdminDtos {

    private UserAdminDtos() {}

    /** Creating an account at a role: committee, security, or a resident added by hand. */
    public record CreateUserRequest(
            @NotBlank(message = "Mobile is required")
            @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit Indian mobile number") String mobile,
            @NotBlank(message = "Name is required") @Size(max = 160, message = "Name is too long") String displayName,
            @NotNull(message = "Role is required") Role role,
            @Size(max = 8) String wing,
            @Size(max = 16) String flatNumber,
            @Email(message = "Enter a valid email") @Size(max = 255) String email
    ) {}

    /** Role, flat link, name, and whether the account may sign in. */
    public record UpdateUserRequest(
            @NotBlank(message = "Name is required") @Size(max = 160, message = "Name is too long") String displayName,
            @NotNull(message = "Role is required") Role role,
            @Size(max = 8) String wing,
            @Size(max = 16) String flatNumber,
            @Email(message = "Enter a valid email") @Size(max = 255) String email,
            boolean isActive,
            Long version
    ) {}

    /** The signed-in user editing themselves. Mobile, role and flat are not theirs to change. */
    public record UpdateProfileRequest(
            @NotBlank(message = "Name is required") @Size(max = 160, message = "Name is too long") String displayName,
            @Email(message = "Enter a valid email") @Size(max = 255) String email
    ) {}

    public record AdminUserResponse(
            UUID id,
            String mobile,
            String displayName,
            String email,
            Role role,
            UUID flatId,
            String wing,
            String flatNumber,
            boolean isActive,
            Instant lastLoginAt,
            Instant createdDate,
            Long version
    ) {
        public static AdminUserResponse from(User u) {
            var flat = u.getFlat();
            return new AdminUserResponse(u.getId(), u.getMobile(), u.getDisplayName(), u.getEmail(), u.getRole(),
                    flat != null ? flat.getId() : null, flat != null ? flat.getWing() : null,
                    flat != null ? flat.getFlatNumber() : null, u.isActive(), u.getLastLoginAt(),
                    u.getCreatedDate(), u.getVersion());
        }
    }
}
