package com.prideuniversal.domain.gate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyHelpAttendanceRepository extends JpaRepository<DailyHelpAttendance, UUID> {

    Optional<DailyHelpAttendance> findFirstByDailyHelpIdAndExitTimeIsNull(UUID dailyHelpId);

    @Query("select count(a) from DailyHelpAttendance a where a.entryTime >= :from and a.entryTime < :to")
    long countBetween(@Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("select a.dailyHelpId from DailyHelpAttendance a where a.exitTime is null")
    List<UUID> insideNow();

    @Query("""
            select a from DailyHelpAttendance a where a.dailyHelpId = :helpId
            order by a.entryTime desc limit 62
            """)
    List<DailyHelpAttendance> recent(@Param("helpId") UUID helpId);
}
