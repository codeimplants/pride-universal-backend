package com.prideuniversal.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SosRepository extends JpaRepository<SosAlert, UUID> {

    @Query("select s from SosAlert s order by s.createdDate desc limit 100")
    List<SosAlert> latest();

    @Query("select s from SosAlert s where s.raisedById = :userId order by s.createdDate desc limit 50")
    List<SosAlert> mine(@Param("userId") UUID userId);

    @Query("select count(s) from SosAlert s where s.createdDate >= :from and s.createdDate < :to")
    long countBetween(@Param("from") Instant from, @Param("to") Instant to);
}
