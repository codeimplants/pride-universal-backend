package com.prideuniversal.domain.gate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface BlacklistRepository extends JpaRepository<BlacklistEntry, UUID> {
    @Query("select b from BlacklistEntry b where b.active = true order by b.createdDate desc")
    List<BlacklistEntry> allActive();

    @Query("""
            select b from BlacklistEntry b where b.active = true
              and ((:mobile is not null and b.mobile = :mobile)
                or (:vehicle is not null and b.vehicleNumber = :vehicle))
            """)
    List<BlacklistEntry> match(@Param("mobile") String mobile, @Param("vehicle") String vehicle);
}
