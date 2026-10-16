package com.senvia.doangiuaky.engagement.repository;

import com.senvia.doangiuaky.engagement.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByEventId(UUID eventId);

    List<Notification> findAllByEventIdOrderByReceiverIdAsc(UUID eventId);
}
