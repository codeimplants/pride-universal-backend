package com.prideuniversal.domain.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n order by n.createdDate desc limit 50")
    List<Notification> latest();
}
