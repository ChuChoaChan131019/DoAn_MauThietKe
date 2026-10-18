package com.senvia.doangiuaky.ordering.api;

public interface ReviewOrderApi {
    ReviewableOrderItem requireReviewableItem(Long orderItemId, Long productId);
}