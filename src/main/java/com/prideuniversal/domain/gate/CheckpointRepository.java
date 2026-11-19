package com.prideuniversal.domain.gate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface CheckpointRepository extends JpaRepository<PatrolCheckpoint, UUID> {
    @Query("select c from PatrolCheckpoint c where c.active = true order by c.name")
    List<PatrolCheckpoint> allActive();

    java.util.Optional<PatrolCheckpoint> findByCodeAndActiveTrue(String code);

    boolean existsByCode(String code);
}
