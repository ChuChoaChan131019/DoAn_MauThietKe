package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;

import java.math.BigDecimal;
import java.util.List;

public record CheckoutReceipt(List<ShopReceipt> orders, BigDecimal grandTotal) {
    public CheckoutReceipt {
        orders = List.copyOf(orders);
    }

    public record ShopReceipt(String shopName, String orderCode, BigDecimal amount, PaymentMethod method,
            PaymentStatus paymentStatus, String transactionReference) {

    }
}
