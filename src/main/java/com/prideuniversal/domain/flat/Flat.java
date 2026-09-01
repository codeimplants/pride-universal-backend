package com.prideuniversal.domain.flat;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A flat in the society. The root of most other data: bills, complaints and
 * visitors all hang off it.
 *
 * <p>{@code wing} is stored as a plain code with a foreign key to the
 * {@code wing} reference table rather than as a {@code @ManyToOne}. That is a
 * deliberate trade: the code is the natural key, it is two characters, and
 * modelling it as an association would add a join to nearly every query for no
 * information gain. The FK still guarantees only real wings can be stored.
 */
@Entity
@Table(name = "flat")
@Getter
@Setter
public class Flat extends BaseEntity {

    @Column(nullable = false, length = 8)
    private String wing;

    @Column(name = "flat_number", nullable = false, length = 16)
    private String flatNumber;

    private Integer floor;

    @Column(name = "area_sqft", precision = 10, scale = 2)
    private BigDecimal areaSqft;

    @Column(name = "owner_name", nullable = false, length = 160)
    private String ownerName;

    @Column(name = "owner_contact", length = 10)
    private String ownerContact;

    @Column(name = "owner_alternate_contact", length = 10)
    private String ownerAlternateContact;

    @Column(name = "is_tenant", nullable = false)
    private boolean tenant = false;

    @Column(name = "tenant_name", length = 160)
    private String tenantName;

    @Column(name = "tenant_contact", length = 10)
    private String tenantContact;

    @Column(name = "is_staying", nullable = false)
    private boolean staying = true;

    @Column(name = "two_wheeler", nullable = false)
    private boolean twoWheeler = false;

    @Column(name = "two_wheeler_count", nullable = false)
    private int twoWheelerCount = 0;

    @Column(name = "two_wheeler_numbers", length = 255)
    private String twoWheelerNumbers;

    @Column(name = "four_wheeler", nullable = false)
    private boolean fourWheeler = false;

    @Column(name = "four_wheeler_count", nullable = false)
    private int fourWheelerCount = 0;

    @Column(name = "four_wheeler_numbers", length = 255)
    private String fourWheelerNumbers;

    @Column(name = "permanent_parking", nullable = false)
    private boolean permanentParking = false;

    @Column(name = "share_certificate_number", length = 64)
    private String shareCertificateNumber;

    @Column(name = "membership_date")
    private LocalDate membershipDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(columnDefinition = "text")
    private String notes;

    /** The person actually living here - tenant if let, owner otherwise. */
    public String residentName() {
        return tenant && tenantName != null ? tenantName : ownerName;
    }

    /** Every mobile number entitled to log in as this flat. */
    public boolean matchesMobile(String mobile) {
        return mobile != null
                && (mobile.equals(ownerContact)
                 || mobile.equals(tenantContact)
                 || mobile.equals(ownerAlternateContact));
    }
}
