package com.prideuniversal.domain.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Nexus, the Code Implants admin console, reads users and society figures and
 * deletes accounts here, with its service token ({@code ADMIN_SERVICE_TOKEN}; see
 * {@code SecurityConfig.adminConsoleChain}). A user's JWT is never accepted.
 * Every body is {@code {"data": …}}, the envelope Nexus's federation reads, as
 * RestoBill's admin API does.
 *
 * <p>Registered in Nexus with base URL {@code http://127.0.0.1:<port>/api/admin},
 * users path {@code /users}, delete path {@code /users} and deleted-users path
 * {@code /users/deleted} ({@code scripts/connect-nexus.sh}).
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin console (Nexus)")
public class AdminConsoleController {

    private final AdminConsoleService console;

    public AdminConsoleController(AdminConsoleService console) {
        this.console = console;
    }

    private static Map<String, Object> data(Object value) {
        return Map.of("data", value);
    }

    @GetMapping("/users")
    @Operation(summary = "Every account: id, name, phone, registration date, role, flat, last sign-in")
    public Map<String, Object> users() {
        return data(console.users());
    }

    @GetMapping("/users/deleted")
    @Operation(summary = "Ids of deleted accounts, so Nexus can drop their telemetry")
    public Map<String, Object> deletedUsers() {
        return data(console.deletedUsers());
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete an account as its owner would; 404 if it is already gone")
    public void deleteUser(@PathVariable UUID id) {
        console.deleteUser(id);
    }

    @GetMapping("/stats")
    @Operation(summary = "Society totals, plus sign-ups, complaints and visitors per day")
    public Map<String, Object> stats(@RequestParam(defaultValue = "30") int days) {
        return data(console.stats(days));
    }
}
