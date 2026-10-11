package com.senvia.doangiuaky.shopping.dto;

import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.shopping.entity.Favorite;

import java.math.BigDecimal;
import java.time.Instant;

public record FavoriteItemView(
        Long favoriteId,
        Long productId,
        String productName,
        String imageUrl,
        BigDecimal currentPrice,
        String shopName,
        boolean available,
        String unavailableReason,
        Instant addedAt) {

    public static FavoriteItemView from(Favorite favorite, CartProductView product) {
        return new FavoriteItemView(
                favorite.getId(),
                favorite.getProductId(),
                product.productName(),
                product.imageUrl(),
                product.currentPrice(),
                product.shopName(),
                product.isSaleable(),
                product.isSaleable() ? null : availabilityMessage(product),
                favorite.getCreatedAt());
    }

    public static FavoriteItemView unavailable(Favorite favorite) {
        return new FavoriteItemView(
                favorite.getId(),
                favorite.getProductId(),
                "Sản phẩm không còn tồn tại",
                null,
                null,
                null,
                false,
                "Không thể tải thông tin sản phẩm.",
                favorite.getCreatedAt());
    }

    private static String availabilityMessage(CartProductView product) {
        if (product.stockQuantity() <= 0) {
            return "Sản phẩm đã hết hàng.";
        }
        if (!product.ownerActive()) {
            return "Chủ shop đang bị khóa.";
        }
        if (!product.shopApproved()) {
            return "Shop không còn được duyệt.";
        }
        if (!product.categoryActive()) {
            return "Danh mục không còn hoạt động.";
        }
        if (!product.productActive()) {
            return "Sản phẩm không còn được bán.";
        }
        return "Sản phẩm hiện không khả dụng.";
    }
}
