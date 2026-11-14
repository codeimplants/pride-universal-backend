package com.prideuniversal.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SocietyEventRepository extends JpaRepository<SocietyEvent, UUID> {

    List<SocietyEvent> findAllByOrderByStartDateDescCreatedDateDesc();

    Optional<SocietyEvent> findByCarriedFromId(UUID carriedFromId);
}
