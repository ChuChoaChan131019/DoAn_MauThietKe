package com.senvia.doangiuaky.shopping.dto;

import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.shopping.entity.CartItem;

import java.math.BigDecimal;

public record CartItemView(
        Long cartItemId,
        Long productId,
        Long shopId,
        String shopName,
        String productName,
        String imageUrl,
        BigDecimal currentPrice,
        int stockQuantity,
        int quantity,
        BigDecimal subtotal,
        boolean available,
        String availabilityMessage) {

    public static CartItemView from(CartItem item, CartProductView product) {
        BigDecimal subtotal = product.currentPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemView(
                item.getId(),
                item.getProductId(),
                product.shopId(),
                product.shopName(),
                product.productName(),
                product.imageUrl(),
                product.currentPrice(),
                product.stockQuantity(),
                item.getQuantity(),
                subtotal,
                true,
                null);
    }

    public static CartItemView unavailable(CartItem item, String message) {
        return new CartItemView(
                item.getId(),
                item.getProductId(),
                null,
                "Sản phẩm không khả dụng",
                "Sản phẩm không còn bán",
                null,
                BigDecimal.ZERO,
                0,
                item.getQuantity(),
                BigDecimal.ZERO,
                false,
                message);
    }
}
