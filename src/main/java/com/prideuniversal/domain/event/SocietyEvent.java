package com.prideuniversal.domain.event;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A festival or event with its own fund: Ganesh Festival 2026, Kojagiri 2026.
 *
 * <p>Its opening balance is either {@code carriedFromId}'s closing balance, read live so it follows
 * corrections to that event's books, or {@code openingBalance} as typed in. See {@link EventBooks}.
 * Mapped as a plain id, not a {@code @ManyToOne}: the chain is walked over all events at once.
 */
@Entity
@Table(name = "society_event")
@Getter
@Setter
public class SocietyEvent extends BaseEntity {

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "carried_from_id")
    private UUID carriedFromId;

    @Column(name = "opening_balance", nullable = false, precision = 12, scale = 2)
    private BigDecimal openingBalance = BigDecimal.ZERO;

    @Column(name = "is_published", nullable = false)
    private boolean published;

    @Column(name = "created_by", length = 160)
    private String createdBy;
}
