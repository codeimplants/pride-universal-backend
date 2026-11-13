package com.prideuniversal.domain.transaction.dto;

import com.prideuniversal.domain.transaction.LedgerEntry;
import com.prideuniversal.domain.transaction.TxnCategory;
import com.prideuniversal.domain.transaction.TxnType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class LedgerDtos {

    private LedgerDtos() {}

    /** {@code type} is implied by the category; month/year default to the date's. */
    public record LedgerRequest(
            @NotBlank(message = "Wing or fund is required")
            @Pattern(regexp = "(?i)^(A|B|common)$", message = "Wing must be A, B or common") String wing,
            @NotNull(message = "Category is required") TxnCategory category,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be positive")
            @Digits(integer = 10, fraction = 2, message = "Amount has too many digits") BigDecimal amount,
            @Size(max = 2_000, message = "Description is too long") String description,
            @NotNull(message = "Date is required") LocalDate txnDate,
            @Size(max = 128, message = "Reference is too long") String reference,
            @Min(value = 1, message = "Month is 1 to 12") @Max(value = 12, message = "Month is 1 to 12") Integer month,
            @Min(value = 2000, message = "Year looks wrong") @Max(value = 2100, message = "Year looks wrong") Integer year,
            Long version
    ) {}

    public record LedgerEntryResponse(
            UUID id,
            String wing,
            TxnType type,
            TxnCategory category,
            BigDecimal amount,
            String description,
            LocalDate txnDate,
            String reference,
            String recordedBy,
            String receiptUrl,
            int month,
            int year,
            Instant createdDate,
            Long version
    ) {
        public static LedgerEntryResponse from(LedgerEntry t) {
            return new LedgerEntryResponse(t.getId(), t.getWing(), t.getType(), t.getCategory(), t.getAmount(),
                    t.getDescription(), t.getTxnDate(), t.getReference(), t.getRecordedBy(), t.getReceiptUrl(),
                    t.getMonth(), t.getYear(), t.getCreatedDate(), t.getVersion());
        }
    }

    /** The Accounts screen: the period's entries and its totals. */
    public record LedgerPage(
            BigDecimal income,
            BigDecimal expense,
            BigDecimal balance,
            List<LedgerEntryResponse> transactions
    ) {}
}
