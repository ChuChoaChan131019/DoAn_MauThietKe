package com.senvia.doangiuaky.merchant.service;

public final class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String message) {
        super(message);
    }
}
