package com.prideuniversal.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {

    Optional<PushDevice> findByToken(String token);

    List<PushDevice> findAllByUserIdIn(Collection<UUID> userIds);

    long deleteByTokenAndUserId(String token, UUID userId);

    @Modifying
    @Query("delete from PushDevice d where d.token in :tokens")
    int deleteByTokenIn(@Param("tokens") Collection<String> tokens);
}
