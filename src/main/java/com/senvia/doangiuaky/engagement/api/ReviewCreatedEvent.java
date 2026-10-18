package com.senvia.doangiuaky.engagement.api;

import java.time.Instant;
import java.util.UUID;

public record ReviewCreatedEvent(
        UUID eventId,
        Long reviewId,
        Long reviewerId,
        Long productId,
        Long orderItemId,
        Long shopId,
        Long shopOwnerId,
        int rating,
        Instant occurredAt) {
}
