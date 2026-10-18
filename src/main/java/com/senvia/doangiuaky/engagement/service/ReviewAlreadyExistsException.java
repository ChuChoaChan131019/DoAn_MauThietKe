package com.senvia.doangiuaky.engagement.service;

public class ReviewAlreadyExistsException extends RuntimeException {

    public ReviewAlreadyExistsException() {
        super("Đơn hàng này đã được đánh giá.");
    }
}
