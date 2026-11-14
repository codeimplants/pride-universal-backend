package com.prideuniversal.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface NotificationRecipientRepository extends JpaRepository<NotificationRecipient, UUID> {

    /** Rows of {@code [NotificationRecipient, Notification]}, newest first: one member's inbox. */
    @Query("""
            select r, n from NotificationRecipient r join Notification n on n.id = r.notificationId
            where r.userId = :userId
            order by n.createdDate desc
            limit 100
            """)
    List<Object[]> inbox(@Param("userId") UUID userId);

    @Modifying
    @Query("""
            update NotificationRecipient r set r.readAt = :now
            where r.userId = :userId and r.readAt is null and r.notificationId in :ids
            """)
    int markRead(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids, @Param("now") Instant now);

    @Modifying
    @Query("update NotificationRecipient r set r.readAt = :now where r.userId = :userId and r.readAt is null")
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
