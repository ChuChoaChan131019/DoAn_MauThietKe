package com.senvia.doangiuaky.merchant.dto;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;

import java.time.Instant;

/** Detail shown to an Admin when locking or unlocking a shop. */
public record AdminShopDetail(
        Long shopId,
        String shopName,
        String description,
        String logoUrl,
        String phone,
        String address,
        String ownerName,
        ShopStatus status,
        String approvedByName,
        Instant approvedAt,
        String lockReason,
        String lockedByName,
        Instant lockedAt) {
}
