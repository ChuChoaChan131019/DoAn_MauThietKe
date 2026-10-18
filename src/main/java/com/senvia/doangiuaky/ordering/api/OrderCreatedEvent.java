package com.senvia.doangiuaky.ordering.api;

import java.time.Instant;
import java.util.UUID;

public record OrderCreatedEvent(UUID eventId, Long orderId, Long shopId, Long shopOwnerId, Long buyerId,
        Long actorId, String orderCode, String status, Instant occurredAt) {

}
