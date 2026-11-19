package com.prideuniversal.domain.gate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GatePassRepository extends JpaRepository<GatePass, UUID> {

    Optional<GatePass> findByCode(String code);

    boolean existsByCode(String code);

    @Query("select p from GatePass p where p.flatId = :flatId order by p.validUntil desc, p.createdDate desc limit 100")
    List<GatePass> forFlat(@Param("flatId") UUID flatId);

    @Query("select p from GatePass p order by p.createdDate desc limit 200")
    List<GatePass> latest();
}
