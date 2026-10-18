package com.senvia.doangiuaky.ordering.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDetails(OrderSummary order, String receiverName, String receiverPhone, String shippingAddress, String note, PaymentInfo payment, List<Item> items, List<OrderHistoryEntry> history) {
    public OrderDetails {
        items = List.copyOf(items);
        history = List.copyOf(history);
    }

    public record Item(Long orderItemId, Long productId, String productName, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
    }

    public record PaymentInfo(String method, String status, BigDecimal amount, String transactionReference, Instant paidAt, Instant refundedAt) {
    }
}