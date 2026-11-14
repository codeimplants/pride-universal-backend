package com.prideuniversal.domain.business;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** A business run by a member, listed for the rest of the society. See V5__member_business.sql. */
@Entity
@Table(name = "member_business")
@Getter
@Setter
public class MemberBusiness extends BaseEntity {

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(length = 8)
    private String wing;

    @Column(name = "flat_number", length = 16)
    private String flatNumber;

    @Column(name = "owner_name", nullable = false, length = 160)
    private String ownerName;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 32)
    private BusinessCategory category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 255)
    private String offer;

    @Column(name = "offer_until")
    private LocalDate offerUntil;

    @Column(nullable = false, length = 10)
    private String contact;

    @Column(length = 10)
    private String whatsapp;

    @Column(length = 255)
    private String link;

    @Column(length = 120)
    private String hours;

    @Column(nullable = false)
    private boolean delivers;

    @Column(nullable = false, length = 16)
    private BusinessStatus status = BusinessStatus.PENDING;

    @Column(name = "reviewed_by", length = 160)
    private String reviewedBy;

    @Column(name = "review_note", length = 255)
    private String reviewNote;

    /** An offer with no end date runs until it is removed; one that has ended no longer shows as an offer. */
    public boolean offerActive(LocalDate today) {
        return offer != null && (offerUntil == null || !offerUntil.isBefore(today));
    }
}
