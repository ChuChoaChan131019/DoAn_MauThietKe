package com.senvia.doangiuaky.ordering.dto;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutSelectionView(List<Shop> shops, BigDecimal subtotal) {

    public record Shop(Long shopId, String shopName, List<Item> items, BigDecimal subtotal) {
    }

    public record Item(Long cartItemId, String productName, int quantity, BigDecimal subtotal) {
    }
}
