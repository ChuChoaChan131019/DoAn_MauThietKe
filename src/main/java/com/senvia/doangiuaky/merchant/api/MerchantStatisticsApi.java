package com.senvia.doangiuaky.merchant.api;

import java.util.Optional;

/** Shop-owned counts for seller dashboards. */
public interface MerchantStatisticsApi {

    /** Returns the authenticated active user's shop, if one exists. */
    Optional<MerchantShopSummary> findCurrentShopSummary();

    /** Counts all managed products, including hidden and out-of-stock products. */
    long countProductsForShop(Long shopId);
}
