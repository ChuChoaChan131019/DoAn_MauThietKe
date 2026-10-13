package com.senvia.doangiuaky.merchant.dto;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;

import java.time.Instant;

/** Summary shown in the Admin shop-management list. */
public record AdminShopSummary(
        Long shopId,
        String shopName,
        String ownerName,
        ShopStatus status,
        Instant approvedAt,
        Instant lockedAt) {
}
