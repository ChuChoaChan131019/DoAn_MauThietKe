package com.senvia.doangiuaky.merchant.dto;

public record AdminCategoryView(
        Long id,
        String name,
        String description,
        boolean active) {

    public boolean isActive() {
        return active;
    }
}
