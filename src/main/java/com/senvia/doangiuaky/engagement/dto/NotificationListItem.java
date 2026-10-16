package com.senvia.doangiuaky.engagement.dto;

import com.senvia.doangiuaky.engagement.entity.NotificationType;

import java.time.Instant;

public record NotificationListItem(
        Long id,
        NotificationType type,
        String title,
        String content,
        boolean read,
        Instant createdAt) {
}
