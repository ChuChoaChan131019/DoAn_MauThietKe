package com.senvia.doangiuaky.ordering.service;

import java.math.BigDecimal;

/**
 * Signals that an authoritative Merchant price changed after the buyer saw the
 * checkout confirmation. No order-side write may happen before this exception
 * is raised.
 */
public class CheckoutPriceChangedException extends CheckoutPreparationException {

    private final BigDecimal currentGrandTotal;

    public CheckoutPriceChangedException(BigDecimal currentGrandTotal) {
        super("Giá sản phẩm đã thay đổi. Vui lòng kiểm tra tổng tiền mới và xác nhận lại.");
        this.currentGrandTotal = currentGrandTotal;
    }

    public BigDecimal getCurrentGrandTotal() {
        return currentGrandTotal;
    }
}
