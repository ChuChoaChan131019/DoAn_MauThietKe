package com.senvia.doangiuaky.shopping.dto;

import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.shopping.entity.CartItem;

import java.math.BigDecimal;

public record CartItemView(
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
        boolean quantityAdjustable,
        String availabilityMessage) {

    public static CartItemView from(CartItem item, CartProductView product) {
        BigDecimal subtotal = product.currentPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new CartItemView(
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
                true,
                null);
    }

    public static CartItemView unavailable(CartItem item, String message) {
        return new CartItemView(
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
                false,
                message);
    }

    public static CartItemView exceedsStock(CartItem item, CartProductView product) {
        return new CartItemView(
                item.getProductId(),
                product.shopId(),
                product.shopName(),
                product.productName(),
                product.imageUrl(),
                product.currentPrice(),
                product.stockQuantity(),
                item.getQuantity(),
                BigDecimal.ZERO,
                false,
                true,
                "Số lượng trong giỏ vượt tồn kho hiện tại.");
    }
}
