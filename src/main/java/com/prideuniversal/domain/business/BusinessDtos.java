package com.prideuniversal.domain.business;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class BusinessDtos {

    private BusinessDtos() {}

    private static final String MOBILE = "^[6-9][0-9]{9}$";

    /** Create or update a listing. {@code whatsapp} null means "the same number as contact". */
    public record BusinessRequest(
            @NotBlank(message = "Business name is required")
            @Size(max = 160, message = "Business name is too long") String name,
            @NotNull(message = "Choose a category") BusinessCategory category,
            @Size(max = 2_000, message = "Description is too long") String description,
            @Size(max = 255, message = "Offer is too long") String offer,
            LocalDate offerUntil,
            @NotBlank(message = "Contact number is required")
            @Pattern(regexp = MOBILE, message = "Enter a valid 10-digit mobile number") String contact,
            @Pattern(regexp = MOBILE, message = "Enter a valid 10-digit WhatsApp number") String whatsapp,
            @Size(max = 255, message = "Link is too long")
            @Pattern(regexp = "^\\S*$", message = "The link cannot contain spaces") String link,
            @Size(max = 120, message = "Hours are too long") String hours,
            boolean delivers,
            Long version
    ) {}

    /** The committee's decision: approved, rejected or hidden, with an optional note to the owner. */
    public record ReviewRequest(
            @NotNull(message = "Choose a decision") BusinessStatus status,
            @Size(max = 255, message = "Note is too long") String note
    ) {}

    /**
     * {@code offerActive} is false once {@code offerUntil} has passed, so an expired deal stops being
     * advertised without anyone having to remember to remove it. {@code reviewNote} reaches only the
     * owner and the committee.
     */
    public record BusinessResponse(
            UUID id,
            String name,
            BusinessCategory category,
            String description,
            String offer,
            LocalDate offerUntil,
            boolean offerActive,
            String contact,
            String whatsapp,
            String link,
            String hours,
            boolean delivers,
            String ownerName,
            String wing,
            String flatNumber,
            BusinessStatus status,
            String reviewNote,
            boolean isMine,
            Instant createdDate,
            Instant updatedDate,
            Long version
    ) {}
}
