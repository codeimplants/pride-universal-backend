package com.prideuniversal.domain.visitor;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One visit, logged at the gate. Flat details are snapshots, as with complaints. */
@Entity
@Table(name = "visitor")
@Getter
@Setter
public class Visitor extends BaseEntity {

    @Column(name = "visitor_name", nullable = false, length = 160)
    private String visitorName;

    @Column(name = "visitor_contact", length = 10)
    private String visitorContact;

    @Column(nullable = false, length = 24)
    private VisitorPurpose purpose;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(nullable = false, length = 8)
    private String wing;

    @Column(name = "flat_number", nullable = false, length = 16)
    private String flatNumber;

    @Column(name = "resident_name", length = 160)
    private String residentName;

    @Column(nullable = false, length = 16)
    private VisitorStatus status = VisitorStatus.PENDING;

    @Column(name = "entry_time")
    private Instant entryTime;

    @Column(name = "exit_time")
    private Instant exitTime;

    @Column(name = "approved_by", length = 160)
    private String approvedBy;

    @Column(name = "approved_by_id")
    private UUID approvedById;

    @Column(name = "rejected_reason", length = 255)
    private String rejectedReason;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(name = "vehicle_number", length = 24)
    private String vehicleNumber;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_by_security", length = 160)
    private String createdBySecurity;
}
