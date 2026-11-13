package com.prideuniversal.domain.transaction;

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
 * One line in the society's books. {@code wing} is {@code A}, {@code B} or
 * {@code common} (the common fund), which is why it has no FK to the wing table.
 * {@code month}/{@code year} is the accounting period, which defaults to the
 * transaction date's but may differ (October's maintenance banked in November).
 */
@Entity
@Table(name = "financial_transaction")
@Getter
@Setter
public class LedgerEntry extends BaseEntity {

    @Column(nullable = false, length = 8)
    private String wing;

    @Column(nullable = false, length = 16)
    private TxnType type;

    @Column(nullable = false, length = 32)
    private TxnCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(length = 128)
    private String reference;

    @Column(name = "recorded_by", length = 160)
    private String recordedBy;

    @Column(name = "recorded_by_id")
    private UUID recordedById;

    @Column(name = "receipt_url", length = 512)
    private String receiptUrl;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int year;
}
