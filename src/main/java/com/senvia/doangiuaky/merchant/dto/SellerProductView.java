package com.senvia.doangiuaky.merchant.dto;

import com.senvia.doangiuaky.merchant.entity.ProductStatus;

import java.math.BigDecimal;
import java.util.List;

public record SellerProductView(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        boolean categoryActive,
        BigDecimal price,
        int stockQuantity,
        ProductStatus status,
        List<ProductImageView> images) {

    public SellerProductView(Long id, String name, String description, Long categoryId,
                             String categoryName, boolean categoryActive, BigDecimal price,
                             int stockQuantity, ProductStatus status) {
        this(id, name, description, categoryId, categoryName, categoryActive, price,
                stockQuantity, status, List.of());
    }

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }
}
