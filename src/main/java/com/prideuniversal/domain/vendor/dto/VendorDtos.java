package com.prideuniversal.domain.vendor.dto;

import com.prideuniversal.domain.vendor.ServiceType;
import com.prideuniversal.domain.vendor.Vendor;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class VendorDtos {

    private VendorDtos() {}

    private static final String MOBILE = "^[6-9][0-9]{9}$";
    private static final String MOBILE_MESSAGE = "Enter a valid 10-digit Indian mobile number";

    /**
     * {@code approved} is honoured only from the committee; anyone else's
     * recommendation starts unverified. Who added the vendor comes from the token.
     */
    public record VendorRequest(
            @NotBlank(message = "Name is required") @Size(max = 160, message = "Name is too long") String name,
            @NotNull(message = "Service type is required") ServiceType serviceType,
            @NotBlank(message = "Contact is required") @Pattern(regexp = MOBILE, message = MOBILE_MESSAGE) String contact,
            @Pattern(regexp = MOBILE, message = MOBILE_MESSAGE) String alternateContact,
            @Size(max = 5_000, message = "Description is too long") String description,
            @Size(max = 255, message = "Availability is too long") String availability,
            Boolean approved,
            @DecimalMin(value = "0.0", message = "Rating is 0 to 5") @DecimalMax(value = "5.0", message = "Rating is 0 to 5") BigDecimal rating,
            Long version
    ) {}

    public record VendorResponse(
            UUID id,
            String name,
            ServiceType serviceType,
            String contact,
            String alternateContact,
            String description,
            String availability,
            boolean approved,
            String addedByFlat,
            String addedByName,
            UUID addedById,
            BigDecimal rating,
            boolean isActive,
            Instant createdDate,
            Long version
    ) {
        public static VendorResponse from(Vendor v) {
            return new VendorResponse(v.getId(), v.getName(), v.getServiceType(), v.getContact(),
                    v.getAlternateContact(), v.getDescription(), v.getAvailability(), v.isApproved(),
                    v.getAddedByFlat(), v.getAddedByName(), v.getAddedById(), v.getRating(), v.isActive(),
                    v.getCreatedDate(), v.getVersion());
        }
    }
}
