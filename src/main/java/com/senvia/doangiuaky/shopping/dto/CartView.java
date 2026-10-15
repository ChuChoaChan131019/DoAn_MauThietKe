package com.senvia.doangiuaky.shopping.dto;

import java.math.BigDecimal;
import java.util.List;

public record CartView(
        List<CartShopView> shops,
        int totalQuantity,
        BigDecimal subtotal) {

    public static CartView empty() {
        return new CartView(List.of(), 0, BigDecimal.ZERO);
    }

    public boolean hasItems() {
        return shops.stream().anyMatch(shop -> !shop.items().isEmpty());
    }

    public boolean hasUnavailableItems() {
        return shops.stream()
                .flatMap(shop -> shop.items().stream())
                .anyMatch(item -> !item.available());
    }

    public boolean canCheckout() {
        return hasItems() && !hasUnavailableItems();
    }

    public boolean hasCheckoutableItems() {
        return canCheckout();
    }

    public boolean isEmpty() {
        return !hasItems();
    }
}
