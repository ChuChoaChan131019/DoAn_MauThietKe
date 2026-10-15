package com.senvia.doangiuaky.merchant.api;

/** The current account cannot read statistics for the requested shop. */
public class MerchantStatisticsAccessException extends RuntimeException {

    public MerchantStatisticsAccessException(String message) {
        super(message);
    }
}
