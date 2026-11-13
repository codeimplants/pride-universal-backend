package com.prideuniversal.domain.vendor;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A tradesperson or service provider residents recommend to each other. */
@Entity
@Table(name = "vendor")
@Getter
@Setter
public class Vendor extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "service_type", nullable = false, length = 32)
    private ServiceType serviceType;

    @Column(nullable = false, length = 10)
    private String contact;

    @Column(name = "alternate_contact", length = 10)
    private String alternateContact;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String availability;

    /** "Verified" in the app. Only the committee can set it. */
    @Column(nullable = false)
    private boolean approved = false;

    @Column(name = "added_by_flat", length = 16)
    private String addedByFlat;

    @Column(name = "added_by_name", length = 160)
    private String addedByName;

    @Column(name = "added_by_id")
    private UUID addedById;

    @Column(precision = 2, scale = 1)
    private BigDecimal rating;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
