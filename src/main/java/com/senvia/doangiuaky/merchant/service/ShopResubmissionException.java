package com.senvia.doangiuaky.merchant.service;

/** Raised when a shop resubmission cannot be completed. */
public class ShopResubmissionException extends RuntimeException {

    public ShopResubmissionException(String message) {
        super(message);
    }

    public ShopResubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
