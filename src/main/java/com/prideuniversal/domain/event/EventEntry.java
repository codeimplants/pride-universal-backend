package com.prideuniversal.domain.event;

import com.prideuniversal.common.BaseEntity;
import com.prideuniversal.domain.transaction.TxnType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One line in an event's books: vargani collected, or money spent under a head such as Mahaprasad.
 * {@code head} is a known head's code, which the app translates, or free text.
 */
@Entity
@Table(name = "society_event_entry")
@Getter
@Setter
public class EventEntry extends BaseEntity {

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(nullable = false, length = 16)
    private TxnType type;

    @Column(nullable = false, length = 80)
    private String head;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "entry_date")
    private LocalDate entryDate;

    @Column(length = 128)
    private String reference;

    @Column(name = "recorded_by", length = 160)
    private String recordedBy;

    @Column(name = "recorded_by_id")
    private UUID recordedById;
}
