package com.senvia.doangiuaky.merchant.dto;

import java.math.BigDecimal;

/** Public product data used by catalog cards and related-product sections. */
public record CatalogProductCardView(
        Long id,
        String name,
        BigDecimal price,
        String thumbnailUrl,
        String categoryName,
        Long shopId,
        String shopName) {
}
