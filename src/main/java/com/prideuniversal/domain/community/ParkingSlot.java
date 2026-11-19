package com.prideuniversal.domain.community;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A parking slot, assigned to at most one flat. */
@Entity
@Table(name = "parking_slot")
@Getter
@Setter
public class ParkingSlot extends BaseEntity {

    @Column(name = "label", nullable = false, length = 24, unique = true)
    private String label;

    @Column(name = "kind", nullable = false, length = 16)
    private String kind;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(name = "vehicle_number", length = 24)
    private String vehicleNumber;

    @Column(name = "notes", length = 255)
    private String notes;
}
