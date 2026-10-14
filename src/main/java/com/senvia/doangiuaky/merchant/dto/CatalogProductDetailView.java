package com.senvia.doangiuaky.merchant.dto;

import java.math.BigDecimal;
import java.util.List;

/** Public product data used by the product-detail page. */
public record CatalogProductDetailView(
        Long id,
        String name,
        BigDecimal price,
        String thumbnailUrl,
        String categoryName,
        Long shopId,
        String shopName,
        String description,
        int stock,
        List<PublicProductImageView> images,
        PublicShopView shop) {

    public CatalogProductDetailView {
        images = images == null ? List.of() : List.copyOf(images);
    }
}
