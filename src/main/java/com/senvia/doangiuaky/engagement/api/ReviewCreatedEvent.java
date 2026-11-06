package com.senvia.doangiuaky.engagement.api;

import java.time.Instant;
import java.util.UUID;

/** Public event emitted after a valid review has been persisted. */
public record ReviewCreatedEvent(
        UUID eventId,
        Long reviewId,
        Long productId,
        short rating,
        Instant occurredAt) {
}
