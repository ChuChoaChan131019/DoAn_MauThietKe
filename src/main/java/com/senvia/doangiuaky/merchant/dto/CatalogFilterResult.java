package com.senvia.doangiuaky.merchant.dto;

import java.util.List;
import java.util.Map;

/** Internal result for rendering the public catalog filter. */
public record CatalogFilterResult(
        List<CatalogProductCardView> products,
        Map<String, Long> shopCounts,
        boolean categoryAvailable) {

    public CatalogFilterResult {
        products = List.copyOf(products);
        shopCounts = Map.copyOf(shopCounts);
    }
}
