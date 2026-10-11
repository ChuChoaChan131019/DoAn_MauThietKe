package com.senvia.doangiuaky.merchant.service;

/** Raised when the actor is not the active owner allowed to resubmit a shop. */
public class ShopResubmissionAuthorizationException extends RuntimeException {

    public ShopResubmissionAuthorizationException(String message) {
        super(message);
    }
}
