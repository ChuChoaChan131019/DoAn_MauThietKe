package com.senvia.doangiuaky.ordering.strategy;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class BankTransferStrategy implements PaymentStrategy {
    @Override
    public PaymentMethod supportedMethod() {
        return PaymentMethod.BANK_TRANSFER;
    }

    @Override
    public PaymentResult initialize(String orderCode, BigDecimal amount) {
        validateOrderCode(orderCode);

        String reference = "SENVIA-" + orderCode;

        return new PaymentResult(supportedMethod(), amount, PaymentStatus.PENDING, reference, "Chuyển khoản mô phỏng. Nội dung chuyển khoản: " + reference + ". Chờ gian hàng xác nhận đã nhận tiền.");
    }

    private static void validateOrderCode(String orderCode) {
        if (orderCode == null || !orderCode.matches("[A-Za-z0-9-]{1,30}")) {
            throw new IllegalArgumentException("Mã đơn hàng không hợp lệ.");
        }
    }
}
