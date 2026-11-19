package com.prideuniversal.domain.gate;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A parcel left at the gate for a flat, until someone collects it. */
@Entity
@Table(name = "parcel")
@Getter
@Setter
public class Parcel extends BaseEntity {

    @Column(name = "flat_id", nullable = false)
    private UUID flatId;

    @Column(nullable = false, length = 8)
    private String wing;

    @Column(name = "flat_number", nullable = false, length = 16)
    private String flatNumber;

    @Column(length = 80)
    private String carrier;

    @Column(length = 255)
    private String description;

    @Column(name = "received_by", length = 160)
    private String receivedBy;

    @Column(name = "collected_at")
    private Instant collectedAt;

    @Column(name = "collected_by", length = 160)
    private String collectedBy;
}
