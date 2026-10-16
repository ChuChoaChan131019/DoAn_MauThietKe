package com.senvia.doangiuaky.ordering.dto;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDetails(Long orderId, PaymentMethod method, PaymentStatus status, BigDecimal amount,
        String transactionReference, Instant paidAt, Instant refundedAt) {
}
