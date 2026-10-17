package com.senvia.doangiuaky.ordering.api;

import java.time.Instant;
import java.util.UUID;

public record OrderStatusChangedEvent(UUID eventId, Long orderId, Long shopId, Long buyerId, Long actorId, String orderCode, String oldStatus, String newStatus, Instant occurredAt) {
}