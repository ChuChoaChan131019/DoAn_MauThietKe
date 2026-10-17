package com.senvia.doangiuaky.ordering.api;

import java.math.BigDecimal;

/** Thông tin tối thiểu của một đơn trong danh sách đã kiểm tra quyền. */
public record OrderListEntry(
        String orderCode,
        String date,
        Long shopId,
        String shopName,
        Long buyerId,
        String status,
        BigDecimal totalAmount) {
}
