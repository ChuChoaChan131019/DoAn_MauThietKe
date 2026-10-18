package com.senvia.doangiuaky.engagement.dto;

public record ReviewFormView(
        Long orderItemId,
        Long productId,
        String productName,
        String productImageUrl,
        boolean alreadyReviewed) {
}
