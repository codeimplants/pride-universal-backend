package com.prideuniversal.domain.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WingSettingsRepository extends JpaRepository<WingSettings, UUID> {

    Optional<WingSettings> findByWing(String wing);

    boolean existsByWing(String wing);
}
