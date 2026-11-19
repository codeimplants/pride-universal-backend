package com.prideuniversal.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmenityBookingRepository extends JpaRepository<AmenityBooking, UUID> {

    @Query("""
            select b from AmenityBooking b where b.amenityId = :amenityId and b.bookingDate = :day
              and b.cancelled = false order by b.startTime
            """)
    List<AmenityBooking> onDay(@Param("amenityId") UUID amenityId, @Param("day") LocalDate day);

    @Query("""
            select b from AmenityBooking b where b.flatId = :flatId and b.cancelled = false
              and b.bookingDate >= :from order by b.bookingDate, b.startTime
            """)
    List<AmenityBooking> upcomingForFlat(@Param("flatId") UUID flatId, @Param("from") LocalDate from);

    @Query("""
            select b from AmenityBooking b where b.cancelled = false and b.bookingDate >= :from
            order by b.bookingDate, b.startTime limit 200
            """)
    List<AmenityBooking> upcoming(@Param("from") LocalDate from);
}
