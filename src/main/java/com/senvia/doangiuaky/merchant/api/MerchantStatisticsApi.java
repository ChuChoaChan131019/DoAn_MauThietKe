package com.senvia.doangiuaky.merchant.api;

/** Shop-owned counts for seller dashboards. */
public interface MerchantStatisticsApi {

    /** Counts all managed products, including hidden and out-of-stock products. */
    long countProductsForShop(Long shopId);
}
