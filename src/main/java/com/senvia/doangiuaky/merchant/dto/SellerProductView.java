package com.senvia.doangiuaky.merchant.dto;

import com.senvia.doangiuaky.merchant.entity.ProductStatus;

import java.math.BigDecimal;

public record SellerProductView(
        Long id,
        String name,
        String description,
        Long categoryId,
        String categoryName,
        boolean categoryActive,
        BigDecimal price,
        int stockQuantity,
        ProductStatus status) {

    public boolean isActive() {
        return status == ProductStatus.ACTIVE;
    }
}
