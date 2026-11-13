package com.prideuniversal.domain.user;

import com.prideuniversal.common.Role;
import com.prideuniversal.domain.user.dto.UserAdminDtos.AdminUserResponse;
import com.prideuniversal.domain.user.dto.UserAdminDtos.CreateUserRequest;
import com.prideuniversal.domain.user.dto.UserAdminDtos.UpdateProfileRequest;
import com.prideuniversal.domain.user.dto.UserAdminDtos.UpdateUserRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Users", description = "Accounts, roles, and the caller's own profile")
public class UserAdminController {

    private final UserAdminService userAdminService;

    public UserAdminController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping("/api/users")
    @Operation(summary = "All accounts, optionally by role (admin, chairman)")
    public List<AdminUserResponse> list(@RequestParam(required = false) Role role) {
        return userAdminService.list(role);
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an account at a role (admin, chairman)")
    public AdminUserResponse create(@Valid @RequestBody CreateUserRequest request) {
        return userAdminService.create(request);
    }

    @PutMapping("/api/users/{id}")
    @Operation(summary = "Change role, flat, name or active (admin, chairman)")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
        return userAdminService.update(id, request);
    }

    @PutMapping("/api/auth/me")
    @Operation(summary = "Edit your own name and email")
    public AdminUserResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return userAdminService.updateProfile(request);
    }

    @DeleteMapping("/api/auth/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete your own account")
    public void deleteOwnAccount() {
        userAdminService.deleteOwnAccount();
    }
}
