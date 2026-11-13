package com.prideuniversal.domain.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ListingRepository extends JpaRepository<Listing, UUID> {

    /** The live board ({@code idx_mkt_status}). */
    @Query("""
            select l from Listing l
            where l.status = com.prideuniversal.domain.marketplace.ListingStatus.ACTIVE
              and (:type is null or l.listingType = :type)
              and (:category is null or l.category = :category)
            order by l.createdDate desc
            limit 200
            """)
    List<Listing> active(@Param("type") ListingType type, @Param("category") ListingCategory category);

    /** "My listings": everything the caller posted that isn't removed. */
    @Query("""
            select l from Listing l
            where l.postedById = :userId
              and l.status <> com.prideuniversal.domain.marketplace.ListingStatus.REMOVED
            order by l.createdDate desc
            limit 200
            """)
    List<Listing> postedBy(@Param("userId") UUID userId);
}
