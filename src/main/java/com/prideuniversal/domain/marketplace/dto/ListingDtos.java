package com.prideuniversal.domain.marketplace.dto;

import com.prideuniversal.domain.marketplace.Listing;
import com.prideuniversal.domain.marketplace.ListingCategory;
import com.prideuniversal.domain.marketplace.ListingStatus;
import com.prideuniversal.domain.marketplace.ListingType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ListingDtos {

    private ListingDtos() {}

    /**
     * Contact name and number default to the poster's own; wing and flat always
     * come from the poster's flat. {@code status} is read on update only (mark sold).
     */
    public record ListingRequest(
            @NotBlank(message = "Title is required") @Size(max = 255, message = "Title is too long") String title,
            @Size(max = 5_000, message = "Description is too long") String description,
            @NotNull(message = "Category is required") ListingCategory category,
            ListingType listingType,
            @DecimalMin(value = "0.0", message = "Price cannot be negative")
            @Digits(integer = 10, fraction = 2, message = "Price has too many digits") BigDecimal price,
            boolean isFree,
            @Size(max = 160, message = "Name is too long") String contactName,
            @Pattern(regexp = "^[6-9][0-9]{9}$", message = "Enter a valid 10-digit Indian mobile number") String contactNumber,
            ListingStatus status,
            Long version
    ) {}

    public record ListingResponse(
            UUID id,
            String title,
            String description,
            ListingCategory category,
            BigDecimal price,
            boolean isFree,
            ListingType listingType,
            String contactName,
            String contactNumber,
            String wing,
            String flatNumber,
            UUID postedById,
            String photoUrl,
            ListingStatus status,
            Instant createdDate,
            Long version
    ) {
        public static ListingResponse from(Listing l) {
            return new ListingResponse(l.getId(), l.getTitle(), l.getDescription(), l.getCategory(), l.getPrice(),
                    l.isFree(), l.getListingType(), l.getContactName(), l.getContactNumber(), l.getWing(),
                    l.getFlatNumber(), l.getPostedById(), l.getPhotoUrl(), l.getStatus(), l.getCreatedDate(),
                    l.getVersion());
        }
    }
}
