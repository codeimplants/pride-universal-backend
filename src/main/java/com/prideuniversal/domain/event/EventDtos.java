package com.prideuniversal.domain.event;

import com.prideuniversal.domain.transaction.TxnType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class EventDtos {

    private EventDtos() {}

    /**
     * Create or update an event. With {@code carriedFromId} the opening balance is that event's
     * closing balance and {@code openingBalance} is ignored; without it, {@code openingBalance}
     * (default 0) is the amount brought forward. A null {@code isPublished} is a draft.
     */
    public record EventRequest(
            @NotBlank(message = "Name is required")
            @Size(max = 160, message = "Name is too long") String name,
            @NotNull(message = "Start date is required") LocalDate startDate,
            LocalDate endDate,
            @Size(max = 4_000, message = "Description is too long") String description,
            UUID carriedFromId,
            @Digits(integer = 10, fraction = 2, message = "Opening balance has too many digits") BigDecimal openingBalance,
            Boolean isPublished,
            Long version
    ) {}

    public record EntryRequest(
            @NotNull(message = "Choose collection or expense") TxnType type,
            @NotBlank(message = "Head is required")
            @Size(max = 80, message = "Head is too long") String head,
            @Size(max = 255, message = "Description is too long") String description,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "0.01", message = "Amount must be positive")
            @Digits(integer = 10, fraction = 2, message = "Amount has too many digits") BigDecimal amount,
            LocalDate entryDate,
            @Size(max = 128, message = "Reference is too long") String reference,
            Long version
    ) {}

    public record EventRef(UUID id, String name) {}

    /** A row on the events list; {@code openingBalance} is the effective one, carried or typed. */
    public record EventSummary(
            UUID id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            boolean isPublished,
            BigDecimal openingBalance,
            EventRef carriedFrom,
            EventRef carriedTo,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal closingBalance
    ) {}

    public record EventDetail(
            UUID id,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            String description,
            boolean isPublished,
            BigDecimal openingBalance,
            EventRef carriedFrom,
            EventRef carriedTo,
            BigDecimal income,
            BigDecimal expense,
            BigDecimal closingBalance,
            String createdBy,
            Instant updatedDate,
            Long version,
            List<EntryResponse> entries
    ) {}

    public record EntryResponse(
            UUID id,
            TxnType type,
            String head,
            String description,
            BigDecimal amount,
            LocalDate entryDate,
            String reference,
            String recordedBy,
            Instant createdDate,
            Long version
    ) {
        static EntryResponse from(EventEntry e) {
            return new EntryResponse(e.getId(), e.getType(), e.getHead(), e.getDescription(), e.getAmount(),
                    e.getEntryDate(), e.getReference(), e.getRecordedBy(), e.getCreatedDate(), e.getVersion());
        }
    }
}
