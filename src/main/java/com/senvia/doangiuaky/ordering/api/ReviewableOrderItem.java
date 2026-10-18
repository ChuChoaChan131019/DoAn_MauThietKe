package com.senvia.doangiuaky.ordering.api;

public record ReviewableOrderItem(Long orderItemId, Long orderId, String orderCode, Long buyerId, Long shopId, Long productId) {
}