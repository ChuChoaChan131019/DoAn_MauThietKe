package com.senvia.doangiuaky.merchant.dto;

/** Public shop data used by product detail and the public shop page. */
public record PublicShopView(
        Long id,
        String name,
        String description,
        String logoUrl,
        String address) {
}
