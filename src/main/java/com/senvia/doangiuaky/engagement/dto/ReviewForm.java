package com.senvia.doangiuaky.engagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ReviewForm(
        @NotNull @Positive Long orderItemId,
        @NotNull @Positive Long productId,
        @NotNull @Min(1) @Max(5) Integer rating,
        String comment) {
}
