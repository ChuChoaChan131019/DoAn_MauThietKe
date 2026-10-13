package com.senvia.doangiuaky.shopping.dto;

public record CartItemMutationResult(
        CartItemView item,
        boolean quantityClamped,
        String message) {
}
