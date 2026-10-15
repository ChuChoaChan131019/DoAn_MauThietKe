package com.senvia.doangiuaky.merchant.dto;

import java.util.List;
import java.util.Map;

/** Internal result for rendering the public catalog filter. */
public record CatalogFilterResult(
        List<CatalogProductCardView> products,
        Map<String, Long> shopCounts,
        boolean categoryAvailable,
        long totalItems,
        int totalPages,
        int currentPage,
        int pageSize) {

    public CatalogFilterResult {
        products = List.copyOf(products);
        shopCounts = Map.copyOf(shopCounts);
    }

    public static CatalogFilterResult empty(boolean categoryAvailable, int pageSize) {
        return new CatalogFilterResult(List.of(), Map.of(), categoryAvailable, 0, 1, 1, pageSize);
    }
}
