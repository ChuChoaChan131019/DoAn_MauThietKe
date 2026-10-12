package com.senvia.doangiuaky.merchant.service;

/** Raised when the actor is not an active Admin allowed to lock or unlock shops. */
public class ShopLockAuthorizationException extends RuntimeException {

    public ShopLockAuthorizationException(String message) {
        super(message);
    }
}
