package com.prideuniversal.domain.flat.dto;

import com.prideuniversal.domain.flat.Flat;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request and response shapes for the flat endpoints.
 *
 * <h2>Why there are two response records</h2>
 * This is the split that the original security finding demands. A flat row
 * carries owner and tenant mobile numbers, vehicle registrations and share
 * certificate numbers. In the base44 app all of it was readable by anyone who
 * could reach the URL.
 *
 * <p>So the API exposes two views and the service decides which you get:
 * <ul>
 *   <li>{@link FlatSummary} — the resident-facing directory. Who lives where.
 *       No phone numbers, no vehicle registrations, no notes.</li>
 *   <li>{@link FlatDetail} — the full record. Committee members, or a resident
 *       looking at their own flat.</li>
 * </ul>
 *
 * <p>The important property is that {@code FlatSummary} has no field to leak.
 * Filtering by nulling out fields on a single fat DTO would work until someone
 * adds a field and forgets the nulling; a type that never held the data cannot
 * be made to spill it.
 */
public final class FlatDtos {

    private FlatDtos() {}

    private static final String MOBILE_REGEX = "^[6-9][0-9]{9}$";
    private static final String MOBILE_MESSAGE = "Enter a valid 10-digit Indian mobile number";

    // =====================================================================
    // Request
    // =====================================================================

    /**
     * Used for both create and update.
     *
     * <p>{@code version} is the optimistic-locking token: null on create (there
     * is no row yet to have a version), required on update. The service, not
     * bean validation, enforces that — a single annotation cannot say
     * "required here, forbidden there" without validation groups, and the rule
     * is clearer stated once in the service than spread across two records
     * that would otherwise be identical.
     *
     * <p>Note what is absent: {@code id}, {@code createdDate} and
     * {@code isActive}. A client cannot set them. Deactivation is a separate
     * endpoint with its own permission, not a field anyone can flip.
     */
    public record FlatRequest(

            @NotBlank(message = "Wing is required")
            @Size(max = 8, message = "Wing code is too long")
            String wing,

            @NotBlank(message = "Flat number is required")
            @Size(max = 16, message = "Flat number is too long")
            String flatNumber,

            @Min(value = 0, message = "Floor cannot be negative")
            @Max(value = 200, message = "Floor looks wrong")
            Integer floor,

            @DecimalMin(value = "0.0", inclusive = false, message = "Area must be positive")
            @Digits(integer = 8, fraction = 2, message = "Area has too many digits")
            BigDecimal areaSqft,

            @NotBlank(message = "Owner name is required")
            @Size(max = 160, message = "Owner name is too long")
            String ownerName,

            @Pattern(regexp = MOBILE_REGEX, message = MOBILE_MESSAGE)
            String ownerContact,

            @Pattern(regexp = MOBILE_REGEX, message = MOBILE_MESSAGE)
            String ownerAlternateContact,

            boolean isTenant,

            @Size(max = 160, message = "Tenant name is too long")
            String tenantName,

            @Pattern(regexp = MOBILE_REGEX, message = MOBILE_MESSAGE)
            String tenantContact,

            boolean isStaying,

            boolean twoWheeler,

            @Min(value = 0, message = "Count cannot be negative")
            @Max(value = 20, message = "That is a lot of two-wheelers")
            int twoWheelerCount,

            @Size(max = 255, message = "Too many registration numbers")
            String twoWheelerNumbers,

            boolean fourWheeler,

            @Min(value = 0, message = "Count cannot be negative")
            @Max(value = 20, message = "That is a lot of cars")
            int fourWheelerCount,

            @Size(max = 255, message = "Too many registration numbers")
            String fourWheelerNumbers,

            boolean permanentParking,

            @Size(max = 64, message = "Share certificate number is too long")
            String shareCertificateNumber,

            LocalDate membershipDate,

            String notes,

            Long version
    ) {}

    // =====================================================================
    // Responses
    // =====================================================================

    /** The directory view. Deliberately contains nothing worth stealing. */
    public record FlatSummary(
            UUID id,
            String wing,
            String flatNumber,
            Integer floor,
            String residentName,
            boolean isTenant,
            boolean isActive
    ) {
        public static FlatSummary from(Flat flat) {
            return new FlatSummary(
                    flat.getId(),
                    flat.getWing(),
                    flat.getFlatNumber(),
                    flat.getFloor(),
                    // The person actually living there, which is the only name
                    // a neighbour has any business seeing.
                    flat.residentName(),
                    flat.isTenant(),
                    flat.isActive());
        }
    }

    /** The full record. Committee, or the resident's own flat. */
    public record FlatDetail(
            UUID id,
            String wing,
            String flatNumber,
            Integer floor,
            BigDecimal areaSqft,
            String ownerName,
            String ownerContact,
            String ownerAlternateContact,
            boolean isTenant,
            String tenantName,
            String tenantContact,
            boolean isStaying,
            boolean twoWheeler,
            int twoWheelerCount,
            String twoWheelerNumbers,
            boolean fourWheeler,
            int fourWheelerCount,
            String fourWheelerNumbers,
            boolean permanentParking,
            String shareCertificateNumber,
            LocalDate membershipDate,
            boolean isActive,
            String notes,
            /** Echoed back so the client can send it on the next update. */
            Long version
    ) {
        public static FlatDetail from(Flat flat) {
            return new FlatDetail(
                    flat.getId(),
                    flat.getWing(),
                    flat.getFlatNumber(),
                    flat.getFloor(),
                    flat.getAreaSqft(),
                    flat.getOwnerName(),
                    flat.getOwnerContact(),
                    flat.getOwnerAlternateContact(),
                    flat.isTenant(),
                    flat.getTenantName(),
                    flat.getTenantContact(),
                    flat.isStaying(),
                    flat.isTwoWheeler(),
                    flat.getTwoWheelerCount(),
                    flat.getTwoWheelerNumbers(),
                    flat.isFourWheeler(),
                    flat.getFourWheelerCount(),
                    flat.getFourWheelerNumbers(),
                    flat.isPermanentParking(),
                    flat.getShareCertificateNumber(),
                    flat.getMembershipDate(),
                    flat.isActive(),
                    flat.getNotes(),
                    flat.getVersion());
        }
    }
}
