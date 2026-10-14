package com.senvia.doangiuaky.ordering.service;

public class CheckoutPreparationException extends RuntimeException {
    public CheckoutPreparationException(String message) {
        super(message);
    }

    public CheckoutPreparationException(String message, Throwable cause) {
        super(message, cause);
    }
}