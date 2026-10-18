package com.senvia.doangiuaky.engagement.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReviewPageView(
        Long productId,
        String productName,
        BigDecimal averageRating,
        long reviewCount,
        List<ReviewListItem> reviews,
        int currentPage,
        int totalPages) {

    public ReviewPageView {
        reviews = List.copyOf(reviews);
    }
}
