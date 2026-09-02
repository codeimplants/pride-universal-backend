package com.prideuniversal.domain.bill;

import com.prideuniversal.common.BaseEntity;
import com.prideuniversal.domain.flat.Flat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One month's maintenance charge for one flat.
 *
 * <h2>Why wing / flat number / owner name are duplicated here</h2>
 * They already live on {@link Flat}, and copying them looks like a
 * normalisation mistake. It is deliberate: a bill is a <em>financial record of
 * a moment</em>. If a flat is sold in March, the January bill must still show
 * who owed the money in January. Joining to the flat would silently rewrite
 * history every time the register is edited.
 *
 * <p>The rule of thumb: normalise data you want to stay current, denormalise
 * data you want to stay true.
 *
 * <h2>Concurrency</h2>
 * The inherited {@code @Version} column is what stops two committee members
 * marking the same bill paid from overwriting each other — see
 * {@code MaintenanceBillService.markPaid}.
 */
@Entity
@Table(name = "maintenance_bill")
@Getter
@Setter
public class MaintenanceBill extends BaseEntity {

    /**
     * LAZY: listing a month's bills needs the denormalised fields below, not
     * the flat. Fetching it eagerly would add a join to every row of every
     * billing screen.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "flat_id", nullable = false)
    private Flat flat;

    /**
     * The same column mapped read-only, so the owning flat's id is available
     * without touching the association.
     *
     * <p>Needed because the {@code @Id} above is field-mapped: calling
     * {@code getFlat().getId()} on a lazy proxy would initialise it, turning
     * the permission check on a list of bills into an N+1 storm. Marked
     * {@code insertable = false, updatable = false} because {@link #flat} owns
     * the column — two writable mappings of one column is how you get
     * "repeated column" mapping errors.
     */
    @Column(name = "flat_id", insertable = false, updatable = false)
    private UUID flatId;

    @Column(nullable = false, length = 8)
    private String wing;

    @Column(name = "flat_number", nullable = false, length = 16)
    private String flatNumber;

    @Column(name = "owner_name", length = 160)
    private String ownerName;

    @Column(nullable = false)
    private int month;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "late_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal lateFee = BigDecimal.ZERO;

    @Column(nullable = false, length = 16)
    private BillStatus status = BillStatus.PENDING;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "paid_date")
    private LocalDate paidDate;

    @Column(name = "payment_mode", length = 24)
    private PaymentMode paymentMode;

    @Column(name = "payment_reference", length = 128)
    private String paymentReference;

    @Column(name = "receipt_number", length = 64)
    private String receiptNumber;

    @Column(columnDefinition = "text")
    private String notes;

    /** Total owed including any late fee applied by the overdue job. */
    public BigDecimal totalDue() {
        return amount.add(lateFee == null ? BigDecimal.ZERO : lateFee);
    }

    public boolean isSettled() {
        return status == BillStatus.PAID || status == BillStatus.WAIVED;
    }
}
