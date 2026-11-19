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

/** A flat booking an amenity for a slot on a day. */
@Entity
@Table(name = "amenity_booking")
@Getter
@Setter
public class AmenityBooking extends BaseEntity {

    @Column(name = "amenity_id", nullable = false)
    private UUID amenityId;

    @Column(name = "flat_id")
    private UUID flatId;

    @Column(name = "flat_label", length = 24)
    private String flatLabel;

    @Column(name = "booked_by", length = 160)
    private String bookedBy;

    @Column(name = "booked_by_id")
    private UUID bookedById;

    @Column(name = "booking_date", nullable = false)
    private LocalDate bookingDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "purpose", length = 255)
    private String purpose;

    @Column(name = "cancelled", nullable = false)
    private boolean cancelled;
}
