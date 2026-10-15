package com.senvia.doangiuaky.merchant.api;

/** Minimal shop identity and visibility data for other modules. */
public record MerchantShopSummary(
        Long shopId,
        Long ownerId,
        String shopName,
        boolean ownerActive,
        boolean shopApproved) {
}
