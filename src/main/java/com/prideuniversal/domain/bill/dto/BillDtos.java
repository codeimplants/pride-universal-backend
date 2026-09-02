package com.prideuniversal.domain.bill.dto;

import com.prideuniversal.domain.bill.BillStatus;
import com.prideuniversal.domain.bill.MaintenanceBill;
import com.prideuniversal.domain.bill.PaymentMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request and response shapes for maintenance bills.
 *
 * <p>Unlike flats there is only one response record here. A flat's row is seen
 * by neighbours, so it needed a summary view with the contact details removed;
 * a bill is only ever seen by the committee or by the flat that owes it, so
 * there is no audience that should get a reduced version. The access rule does
 * the work instead of the type.
 */
public final class BillDtos {

    private BillDtos() {}

    // =====================================================================
    // Requests
    // =====================================================================

    /** Which billing period to raise bills for. */
    public record GenerateBillsRequest(
            @NotNull(message = "Year is required")
            @Min(value = 2000, message = "Year is out of range")
            @Max(value = 2100, message = "Year is out of range")
            Integer year,

            @NotNull(message = "Month is required")
            @Min(value = 1, message = "Month must be between 1 and 12")
            @Max(value = 12, message = "Month must be between 1 and 12")
            Integer month
    ) {}

    /**
     * Recording a payment.
     *
     * <p>{@code version} is mandatory for the same reason it is on a flat
     * update, and matters more here: two treasurers recording the same cash
     * payment must not both succeed and leave two receipt numbers against one
     * transaction.
     */
    public record MarkPaidRequest(
            @NotNull(message = "Payment mode is required")
            PaymentMode paymentMode,

            /** Defaults to today when omitted. Never allowed to be in the future. */
            LocalDate paidDate,

            @Size(max = 128, message = "Payment reference is too long")
            String paymentReference,

            /** Optional: supply a receipt number, or let the server mint one. */
            @Size(max = 64, message = "Receipt number is too long")
            String receiptNumber,

            @NotNull(message = "version is required; re-fetch the bill and send the version it returned")
            Long version
    ) {}

    // =====================================================================
    // Responses
    // =====================================================================

    /**
     * What a generation run did.
     *
     * <p>{@code skipped} is not an error count — it is the number of flats that
     * already had a bill for the period. Pressing "generate" twice is expected
     * behaviour, and reporting it plainly is friendlier than failing.
     */
    public record GenerationResult(
            int year,
            int month,
            int created,
            int skipped,
            BigDecimal totalBilled
    ) {}

    public record OverdueSweepResult(
            LocalDate asOf,
            int markedOverdue
    ) {}

    public record BillResponse(
            UUID id,
            UUID flatId,
            String wing,
            String flatNumber,
            String ownerName,
            int month,
            int year,
            BigDecimal amount,
            BigDecimal lateFee,
            BigDecimal totalDue,
            BillStatus status,
            LocalDate dueDate,
            LocalDate paidDate,
            PaymentMode paymentMode,
            String paymentReference,
            String receiptNumber,
            String notes,
            Long version
    ) {
        public static BillResponse from(MaintenanceBill bill) {
            return new BillResponse(
                    bill.getId(),
                    // The read-only flat_id mapping, so this does not
                    // initialise the lazy association for every row.
                    bill.getFlatId(),
                    bill.getWing(),
                    bill.getFlatNumber(),
                    bill.getOwnerName(),
                    bill.getMonth(),
                    bill.getYear(),
                    bill.getAmount(),
                    bill.getLateFee(),
                    bill.totalDue(),
                    bill.getStatus(),
                    bill.getDueDate(),
                    bill.getPaidDate(),
                    bill.getPaymentMode(),
                    bill.getPaymentReference(),
                    bill.getReceiptNumber(),
                    bill.getNotes(),
                    bill.getVersion());
        }
    }
}
