package com.prideuniversal.domain.user;

import com.prideuniversal.auth.AuthPrincipal;
import com.prideuniversal.auth.TokenService;
import com.prideuniversal.common.ApiException;
import com.prideuniversal.common.Role;
import com.prideuniversal.common.Text;
import com.prideuniversal.common.Versions;
import com.prideuniversal.domain.flat.Flat;
import com.prideuniversal.domain.flat.FlatRepository;
import com.prideuniversal.domain.user.dto.UserAdminDtos.AdminUserResponse;
import com.prideuniversal.domain.user.dto.UserAdminDtos.CreateUserRequest;
import com.prideuniversal.domain.user.dto.UserAdminDtos.UpdateProfileRequest;
import com.prideuniversal.domain.user.dto.UserAdminDtos.UpdateUserRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Accounts and roles.
 *
 * <p>This is where committee roles come from: they are never self-assigned at
 * login. Admin and chairman manage accounts, under three rules that keep the
 * hierarchy intact:
 * <ul>
 *   <li>nobody grants a role above their own (a chairman cannot mint an admin);</li>
 *   <li>nobody edits an account senior to their own;</li>
 *   <li>nobody changes their own role or deactivates themselves, and the last
 *       active admin cannot be removed — no way to lock the society out.</li>
 * </ul>
 * A role change or deactivation revokes the person's refresh tokens, so it takes
 * effect when their current access token expires.
 *
 * <p>Also the signed-in user's own profile: edit name and email, or delete the
 * account (Google Play requires in-app account deletion).
 */
@Service
public class UserAdminService {

    private static final Logger log = LoggerFactory.getLogger(UserAdminService.class);
    private static final String CAN_MANAGE = "hasAnyRole('ADMIN','CHAIRMAN')";

    private final UserRepository userRepository;
    private final FlatRepository flatRepository;
    private final TokenService tokenService;

    public UserAdminService(UserRepository userRepository, FlatRepository flatRepository, TokenService tokenService) {
        this.userRepository = userRepository;
        this.flatRepository = flatRepository;
        this.tokenService = tokenService;
    }

    // =====================================================================
    // Administration
    // =====================================================================

    @PreAuthorize(CAN_MANAGE)
    @Transactional(readOnly = true)
    public List<AdminUserResponse> list(Role role) {
        List<User> users = role == null
                ? userRepository.findAllByOrderByDisplayNameAsc()
                : userRepository.findAllByRoleOrderByDisplayNameAsc(role);
        return users.stream().map(AdminUserResponse::from).toList();
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public AdminUserResponse create(CreateUserRequest r) {
        AuthPrincipal caller = AuthPrincipal.current();
        requireCanGrant(caller, r.role());
        if (userRepository.existsByMobile(r.mobile())) {
            throw ApiException.conflict("An account with this mobile number already exists.");
        }
        User u = new User();
        u.setMobile(r.mobile());
        u.setDisplayName(r.displayName().trim());
        u.setEmail(Text.blankToNull(r.email()));
        u.setRole(r.role());
        u.setFlat(resolveFlat(r.wing(), r.flatNumber(), r.role()));
        u.setActive(true);
        log.info("{} created a {} account for {}", Text.mask(caller.mobile()), r.role().getCode(), Text.mask(r.mobile()));
        return AdminUserResponse.from(userRepository.save(u));
    }

    @PreAuthorize(CAN_MANAGE)
    @Transactional
    public AdminUserResponse update(UUID id, UpdateUserRequest r) {
        AuthPrincipal caller = AuthPrincipal.current();
        User u = userRepository.findWithFlatById(id).orElseThrow(() -> ApiException.notFound("User", id));
        Versions.requireCurrent(u.getVersion(), r.version(), "user");

        if (!caller.role().atLeast(u.getRole())) {
            throw ApiException.forbidden("You can't change an account senior to your own.");
        }
        requireCanGrant(caller, r.role());
        boolean self = u.getId().equals(caller.userId());
        if (self && (r.role() != u.getRole() || !r.isActive())) {
            throw ApiException.badRequest("You can't change your own role or deactivate yourself.");
        }
        boolean losesAdmin = u.getRole() == Role.ADMIN && (r.role() != Role.ADMIN || !r.isActive());
        if (losesAdmin && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("This is the last active admin. Make someone else admin first.");
        }

        boolean accessChanged = r.role() != u.getRole() || r.isActive() != u.isActive();
        u.setDisplayName(r.displayName().trim());
        u.setEmail(Text.blankToNull(r.email()));
        u.setRole(r.role());
        u.setFlat(resolveFlat(r.wing(), r.flatNumber(), r.role()));
        u.setActive(r.isActive());
        User saved = userRepository.saveAndFlush(u);

        if (accessChanged) {
            tokenService.revokeAll(saved);
            log.info("{} set {} to {} (active={})", Text.mask(caller.mobile()), Text.mask(u.getMobile()),
                    r.role().getCode(), r.isActive());
        }
        return AdminUserResponse.from(saved);
    }

    // =====================================================================
    // The caller's own profile
    // =====================================================================

    @Transactional
    public AdminUserResponse updateProfile(UpdateProfileRequest r) {
        User u = userRepository.findWithFlatById(AuthPrincipal.current().userId())
                .orElseThrow(() -> ApiException.notFound("User"));
        u.setDisplayName(r.displayName().trim());
        u.setEmail(Text.blankToNull(r.email()));
        return AdminUserResponse.from(userRepository.saveAndFlush(u));
    }

    /**
     * Deletes the caller's account: the user row and its sessions. Records the
     * society keeps (the flat register, bills, complaints) are the society's, and
     * lose only the link to the account ({@code ON DELETE SET NULL}). A resident
     * whose number is still on a flat can sign in again later as a new account.
     */
    @Transactional
    public void deleteOwnAccount() {
        AuthPrincipal caller = AuthPrincipal.current();
        User u = userRepository.findById(caller.userId()).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getRole() == Role.ADMIN && u.isActive() && userRepository.countByRoleAndActiveTrue(Role.ADMIN) <= 1) {
            throw ApiException.conflict("You are the only admin. Make someone else admin before deleting your account.");
        }
        tokenService.revokeAll(u);
        userRepository.delete(u);
        log.info("Account deleted at the owner's request: {}", Text.mask(u.getMobile()));
    }

    // =====================================================================

    private static void requireCanGrant(AuthPrincipal caller, Role role) {
        if (!caller.role().atLeast(role)) {
            throw ApiException.forbidden("You can't grant a role above your own.");
        }
    }

    /** Residents must have a flat (ck_user_resident_flat); others may. */
    private Flat resolveFlat(String wing, String flatNumber, Role role) {
        String w = Text.blankToNull(wing);
        String n = Text.blankToNull(flatNumber);
        if (w == null || n == null) {
            if (role == Role.RESIDENT) {
                throw ApiException.badRequest("A resident account must be linked to a flat.");
            }
            return null;
        }
        return flatRepository.findByWingAndFlatNumber(w.toUpperCase(), n)
                .filter(Flat::isActive)
                .orElseThrow(() -> ApiException.badRequest("There is no flat " + w.toUpperCase() + "-" + n + "."));
    }
}
