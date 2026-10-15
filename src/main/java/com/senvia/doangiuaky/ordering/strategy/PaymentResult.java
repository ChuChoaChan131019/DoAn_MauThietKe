package com.senvia.doangiuaky.ordering.strategy;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record PaymentResult(PaymentMethod method, BigDecimal amount, PaymentStatus status, String transactionReference,
        String instruction) {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");

    public PaymentResult {
        Objects.requireNonNull(method, "Payment method must not be null");
        Objects.requireNonNull(status, "Payment status must not be null");
        Objects.requireNonNull(amount, "Payment amount must not be null");

        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new IllegalArgumentException("Số tiền thanh toán không hợp lệ.");
        }

        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Số tiền không được có hai chữ số thập phân.", ex);
        }

        if (transactionReference != null && (transactionReference.isBlank() || transactionReference.length() > 100)) {
            throw new IllegalArgumentException("Mã tham chiếu thanh toán không hợp lệ.");
        }

        if (instruction == null || instruction.isBlank()) {
            throw new IllegalArgumentException("Thiếu hướng dẫn thanh toán.");
        }
    }
}
