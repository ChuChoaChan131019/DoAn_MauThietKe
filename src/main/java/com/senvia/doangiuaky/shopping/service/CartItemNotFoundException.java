package com.senvia.doangiuaky.shopping.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class CartItemNotFoundException extends CartOperationException {

    public CartItemNotFoundException() {
        super("Không tìm thấy mục giỏ hàng.");
    }
}
