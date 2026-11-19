package com.prideuniversal.domain.gate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ScanRepository extends JpaRepository<PatrolScan, UUID> {
    @Query("select count(s) from PatrolScan s where s.createdDate >= :from and s.createdDate < :to")
    long countBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select s from PatrolScan s where s.createdDate >= :since order by s.createdDate desc")
    List<PatrolScan> since(@Param("since") Instant since);
}
