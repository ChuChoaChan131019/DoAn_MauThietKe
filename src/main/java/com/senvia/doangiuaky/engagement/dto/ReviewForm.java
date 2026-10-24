package com.senvia.doangiuaky.engagement.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ReviewForm {

    private Long orderItemId;
    private Long productId;

    @NotNull(message = "Vui lòng chọn số sao đánh giá.")
    @Min(value = 1, message = "Số sao phải từ 1 đến 5.")
    @Max(value = 5, message = "Số sao phải từ 1 đến 5.")
    private Integer rating;

    private String comment;

    public Long getOrderItemId() {
        return orderItemId;
    }

    public void setOrderItemId(Long orderItemId) {
        this.orderItemId = orderItemId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }
}
