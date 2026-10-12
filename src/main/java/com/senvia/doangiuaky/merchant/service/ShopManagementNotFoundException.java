package com.senvia.doangiuaky.merchant.service;

/** Raised when a shop cannot be found in the Admin management scope. */
public class ShopManagementNotFoundException extends RuntimeException {

    public ShopManagementNotFoundException(String message) {
        super(message);
    }
}
