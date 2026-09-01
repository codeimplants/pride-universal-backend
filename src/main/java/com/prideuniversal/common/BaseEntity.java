package com.prideuniversal.common;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;
import org.hibernate.proxy.HibernateProxy;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Shared identity, auditing and optimistic-locking behaviour for all entities.
 *
 * <p>Note on {@code equals}/{@code hashCode}: JPA entities are the classic place
 * where the default implementations bite. Two rules are non-negotiable here:
 * <ul>
 *   <li>{@code hashCode()} returns a <em>constant</em>. An entity's id is null
 *       before persist and populated after, so an id-based hash would change
 *       while the object sits in a {@code HashSet} - and the object would then
 *       be unfindable in the bucket it was filed under.</li>
 *   <li>{@code equals()} unwraps Hibernate proxies before comparing classes.
 *       A lazily-loaded association is a generated subclass, so a naive
 *       {@code getClass() != o.getClass()} check fails against its own entity.</li>
 * </ul>
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_date", nullable = false, updatable = false)
    private Instant createdDate;

    @Column(name = "updated_date", nullable = false)
    private Instant updatedDate;

    /**
     * Optimistic lock. Hibernate increments this on every flush and throws
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * if another transaction got there first. This is what stops two committee
     * members marking the same maintenance bill paid from silently
     * overwriting each other.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdDate == null) {
            createdDate = now;
        }
        updatedDate = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedDate = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Instant getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(Instant createdDate) {
        this.createdDate = createdDate;
    }

    public Instant getUpdatedDate() {
        return updatedDate;
    }

    public void setUpdatedDate(Instant updatedDate) {
        this.updatedDate = updatedDate;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        Class<?> thisType = effectiveClass(this);
        Class<?> otherType = effectiveClass(o);
        if (!thisType.equals(otherType)) {
            return false;
        }
        BaseEntity other = (BaseEntity) o;
        // Transient instances are only ever equal to themselves.
        return id != null && Objects.equals(id, other.getId());
    }

    /**
     * Constant by design - see the class javadoc.
     */
    @Override
    public final int hashCode() {
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object o) {
        return o instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : o.getClass();
    }
}
