package com.senvia.doangiuaky.merchant.api;

import java.time.Instant;
import java.util.UUID;

/** Public event emitted after a shop request is submitted or resubmitted. */
public record ShopRequestedEvent(
        UUID eventId,
        Long shopId,
        Long ownerId,
        String shopName,
        Instant submittedAt) {
}
