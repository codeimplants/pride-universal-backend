package com.prideuniversal.common;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Set;

/**
 * Society roles, ported from the old front-end {@code useRole.js}.
 *
 * <p>The critical difference: in the base44 app this hierarchy lived purely in
 * the browser and was read from {@code sessionStorage}, so anyone could edit
 * their way to admin. Here the role comes from the database, is embedded in a
 * signed JWT, and every permission below is enforced server-side.
 */
public enum Role {

    ADMIN("admin", 100),
    CHAIRMAN("chairman", 90),
    SECRETARY("secretary", 70),
    TREASURER("treasurer", 60),
    COMMITTEE_MEMBER("committee_member", 40),
    SECURITY("security", 20),
    RESIDENT("resident", 10);

    private final String code;
    private final int level;

    Role(String code, int level) {
        this.code = code;
        this.level = level;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    public int getLevel() {
        return level;
    }

    @JsonCreator
    public static Role fromCode(String code) {
        return Arrays.stream(values())
                .filter(r -> r.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown role: " + code));
    }

    /** Spring Security expects authorities prefixed with {@code ROLE_}. */
    public String authority() {
        return "ROLE_" + name();
    }

    // --- Permission groups, mirroring the old useRole() booleans -------------

    public static final Set<Role> MANAGEMENT = Set.of(
            ADMIN, CHAIRMAN, SECRETARY, TREASURER, COMMITTEE_MEMBER);

    public static final Set<Role> FINANCE = Set.of(
            ADMIN, CHAIRMAN, SECRETARY, TREASURER);

    public static final Set<Role> COMMS = Set.of(
            ADMIN, CHAIRMAN, SECRETARY, COMMITTEE_MEMBER);

    public static final Set<Role> MEMBER_ADMIN = Set.of(
            ADMIN, CHAIRMAN, SECRETARY);

    public boolean isManagement() {
        return MANAGEMENT.contains(this);
    }

    public boolean atLeast(Role other) {
        return this.level >= other.level;
    }
}
