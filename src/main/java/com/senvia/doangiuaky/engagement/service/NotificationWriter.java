package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationWriter {

    private final NotificationRepository notificationRepository;

    public NotificationWriter(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void create(UUID eventId, Long receiverId, NotificationType type,
                       String title, String content, Long referenceId) {
        notificationRepository.saveAndFlush(Notification.create(
                eventId, receiverId, type, title, content, referenceId));
    }
}
