package com.senvia.doangiuaky.ordering.dto;

import java.math.BigDecimal;
import java.util.List;

public record SellerOrderView(String id, String date, String customer, String phone, String address, String status, BigDecimal total, PaymentDetails payment, List<Item> items) {
    public SellerOrderView {
        items = List.copyOf(items);
    }

    public record Item(Long productId, String name, BigDecimal price, int quantity, BigDecimal subtotal) {
    }
}