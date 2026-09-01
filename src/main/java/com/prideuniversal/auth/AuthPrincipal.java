package com.prideuniversal.auth;

import com.prideuniversal.common.Role;
import com.prideuniversal.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * The authenticated caller, reconstructed from the JWT on every request.
 *
 * <p>Deliberately a small immutable record rather than the {@code User} entity:
 * it is built from token claims with no database hit, which keeps authenticated
 * requests to a single query for the actual work being done.
 */
public record AuthPrincipal(
        UUID userId,
        String mobile,
        Role role,
        UUID flatId,
        String displayName
) {

    public boolean isManagement() {
        return role.isManagement();
    }

    public boolean isResident() {
        return role == Role.RESIDENT;
    }

    /** True if this caller may see data belonging to the given flat. */
    public boolean canAccessFlat(UUID targetFlatId) {
        return isManagement() || (flatId != null && flatId.equals(targetFlatId));
    }

    // --- Access from anywhere in the call stack -----------------------------

    /** The current caller, or null when the request is unauthenticated. */
    public static AuthPrincipal currentOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        return auth.getPrincipal() instanceof AuthPrincipal p ? p : null;
    }

    /** The current caller, or 401 if there isn't one. */
    public static AuthPrincipal current() {
        AuthPrincipal p = currentOrNull();
        if (p == null) {
            throw ApiException.unauthorized("Not authenticated");
        }
        return p;
    }
}
