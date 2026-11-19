package com.prideuniversal.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AmenityRepository extends JpaRepository<Amenity, UUID> {

    @Query("select a from Amenity a where a.active = true order by a.name")
    List<Amenity> allActive();
}
