package com.senvia.doangiuaky.merchant.api;

/** A rejected stock command; the surrounding order transaction must roll back. */
public class MerchantStockException extends RuntimeException {

    public MerchantStockException(String message) {
        super(message);
    }
}
