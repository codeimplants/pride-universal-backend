package com.prideuniversal.domain.user;

import com.prideuniversal.common.BaseEntity;
import com.prideuniversal.common.Role;
import com.prideuniversal.domain.flat.Flat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * An account that can log in. Mapped to {@code app_user} because {@code user}
 * is a reserved word in Postgres.
 *
 * <p>This is the single source of truth for a caller's role. Nothing the client
 * sends can change it.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 10)
    private String mobile;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Column(length = 255)
    private String email;

    /**
     * Stored as the lowercase code ('admin', 'resident', ...) by
     * {@link com.prideuniversal.common.RoleConverter}, which is auto-applied.
     *
     * <p>Deliberately NOT {@code @Enumerated}: {@code ORDINAL} is a trap
     * (reordering the enum silently reassigns everyone's role) and
     * {@code STRING} writes the uppercase constant name, which the
     * {@code ck_user_role} CHECK constraint rejects.
     */
    @Column(nullable = false, length = 32)
    private Role role = Role.RESIDENT;

    /**
     * LAZY because most requests only need the user's id and role. Fetching the
     * flat eagerly would add a join to every single authenticated request.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flat_id")
    private Flat flat;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;
}
