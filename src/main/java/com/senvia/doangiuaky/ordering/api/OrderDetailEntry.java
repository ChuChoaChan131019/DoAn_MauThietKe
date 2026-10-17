package com.senvia.doangiuaky.ordering.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Chi tiết đơn chỉ được trả sau khi Ordering kiểm tra actor và quyền sở hữu. */
public record OrderDetailEntry(
        String id,
        String date,
        Long shopId,
        Long buyerId,
        String customer,
        String phone,
        String address,
        String status,
        BigDecimal total,
        Payment payment,
        List<Item> items) {

    public OrderDetailEntry {
        items = List.copyOf(items);
    }

    public record Payment(
            String method,
            String status,
            BigDecimal amount,
            String transactionReference,
            Instant paidAt,
            Instant refundedAt) {
    }

    public record Item(
            Long productId,
            String name,
            BigDecimal price,
            int quantity,
            BigDecimal subtotal) {
    }
}
