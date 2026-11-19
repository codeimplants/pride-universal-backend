package com.prideuniversal.domain.gate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ParcelRepository extends JpaRepository<Parcel, UUID> {
    @Query("select p from Parcel p where (:waiting = false or p.collectedAt is null) order by p.createdDate desc limit 200")
    List<Parcel> latest(@Param("waiting") boolean waitingOnly);

    @Query("select p from Parcel p where p.flatId = :flatId order by p.createdDate desc limit 100")
    List<Parcel> forFlat(@Param("flatId") UUID flatId);

    @Query("select count(p) from Parcel p where p.createdDate >= :from and p.createdDate < :to")
    long countBetween(@Param("from") Instant from, @Param("to") Instant to);
}
