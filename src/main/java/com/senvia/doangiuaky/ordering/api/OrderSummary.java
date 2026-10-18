package com.senvia.doangiuaky.ordering.api;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummary(String orderCode, Long buyerId, Long shopId, String status, BigDecimal totalAmount, Instant createdAt) {
}