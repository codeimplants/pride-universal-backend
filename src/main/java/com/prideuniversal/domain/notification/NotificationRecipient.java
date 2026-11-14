package com.prideuniversal.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A notification in one member's inbox, read or not. Not versioned: only its reader changes it. */
@Entity
@Table(name = "notification_recipient")
@Getter
@Setter
public class NotificationRecipient {

    @Id
    @GeneratedValue
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "notification_id", nullable = false, updatable = false)
    private UUID notificationId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "read_at")
    private Instant readAt;

    public static NotificationRecipient of(UUID notificationId, UUID userId) {
        NotificationRecipient r = new NotificationRecipient();
        r.setNotificationId(notificationId);
        r.setUserId(userId);
        return r;
    }
}
