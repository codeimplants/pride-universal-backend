package com.prideuniversal.domain.gate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DailyHelpRepository extends JpaRepository<DailyHelp, UUID> {

    Optional<DailyHelp> findByCode(String code);

    boolean existsByCode(String code);

    Optional<DailyHelp> findFirstByMobileAndActiveTrue(String mobile);

    @Query("select h from DailyHelp h where h.active = true order by h.name")
    List<DailyHelp> allActive();

    @Query("select distinct h from DailyHelp h join h.flatIds f where f = :flatId and h.active = true order by h.name")
    List<DailyHelp> forFlat(@Param("flatId") UUID flatId);
}
