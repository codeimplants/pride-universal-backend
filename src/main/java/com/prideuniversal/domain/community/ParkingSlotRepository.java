package com.prideuniversal.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParkingSlotRepository extends JpaRepository<ParkingSlot, UUID> {

    @Query("select p from ParkingSlot p order by p.label")
    List<ParkingSlot> allSlots();

    List<ParkingSlot> findByFlatIdOrderByLabel(UUID flatId);

    boolean existsByLabel(String label);
}
