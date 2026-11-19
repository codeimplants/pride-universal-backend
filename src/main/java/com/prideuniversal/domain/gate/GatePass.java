package com.prideuniversal.domain.gate;

import com.prideuniversal.common.BaseEntity;
import com.prideuniversal.domain.visitor.VisitorPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** A guest let in ahead of time by a resident: shown at the gate as a 6-digit code. */
@Entity
@Table(name = "gate_pass")
@Getter
@Setter
public class GatePass extends BaseEntity {

    @Column(nullable = false, length = 6, unique = true)
    private String code;

    @Column(name = "flat_id", nullable = false)
    private UUID flatId;

    @Column(nullable = false, length = 8)
    private String wing;

    @Column(name = "flat_number", nullable = false, length = 16)
    private String flatNumber;

    @Column(name = "guest_name", nullable = false, length = 160)
    private String guestName;

    @Column(name = "guest_contact", length = 10)
    private String guestContact;

    @Column(nullable = false, length = 24)
    private VisitorPurpose purpose;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(name = "multi_use", nullable = false)
    private boolean multiUse;

    @Column(nullable = false)
    private int uses;

    @Column(nullable = false)
    private boolean cancelled;

    @Column(length = 500)
    private String notes;

    @Column(name = "created_by", length = 160)
    private String createdBy;

    @Column(name = "created_by_id")
    private UUID createdById;
}
