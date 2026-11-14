package com.prideuniversal.domain.notification;

import com.prideuniversal.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** One app install that may receive push: an Expo push token, its owner and the app language on it. */
@Entity
@Table(name = "push_device")
@Getter
@Setter
public class PushDevice extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255, unique = true)
    private String token;

    @Column(nullable = false, length = 16)
    private String platform;

    @Column(name = "device_name", length = 120)
    private String deviceName;

    @Column(nullable = false, length = 8)
    private String language = "en";

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt = Instant.now();
}
