package com.senvia.doangiuaky.engagement.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductReviewsPage(
        Long productId,
        String productName,
        String shopName,
        String imageUrl,
        BigDecimal averageRating,
        long reviewCount,
        List<ReviewItem> reviews) {

    public ProductReviewsPage {
        reviews = List.copyOf(reviews);
    }

    public record ReviewItem(short rating, String comment, Instant createdAt) {
    }
}
