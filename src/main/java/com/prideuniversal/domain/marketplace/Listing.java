package com.prideuniversal.domain.marketplace;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/** A marketplace post between residents: selling, looking to buy, or giving away. */
@Entity
@Table(name = "marketplace_listing")
@Getter
@Setter
public class Listing extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 32)
    private ListingCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price = BigDecimal.ZERO;

    @Column(name = "is_free", nullable = false)
    private boolean free = false;

    @Column(name = "listing_type", nullable = false, length = 16)
    private ListingType listingType = ListingType.SELL;

    @Column(name = "contact_name", nullable = false, length = 160)
    private String contactName;

    @Column(name = "contact_number", nullable = false, length = 10)
    private String contactNumber;

    @Column(length = 8)
    private String wing;

    @Column(name = "flat_number", length = 16)
    private String flatNumber;

    @Column(name = "posted_by_id")
    private UUID postedById;

    @Column(name = "photo_url", length = 512)
    private String photoUrl;

    @Column(nullable = false, length = 16)
    private ListingStatus status = ListingStatus.ACTIVE;
}
