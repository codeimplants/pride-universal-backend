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

/** Something the society books out by the hour: clubhouse, hall, court. */
@Entity
@Table(name = "amenity")
@Getter
@Setter
public class Amenity extends BaseEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "open_time", nullable = false)
    private LocalTime openTime;

    @Column(name = "close_time", nullable = false)
    private LocalTime closeTime;

    @Column(name = "max_hours", nullable = false)
    private int maxHours;

    @Column(name = "fee", nullable = false)
    private BigDecimal fee;

    @Column(name = "active", nullable = false)
    private boolean active;
}
