package com.senvia.doangiuaky.ordering.strategy;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CodPaymentStrategy implements PaymentStrategy {
    @Override
    public PaymentMethod supportedMethod() {
        return PaymentMethod.COD;
    }

    @Override
    public PaymentResult initialize(String orderCode, BigDecimal amount) {
        validateOrderCode(orderCode);

        return new PaymentResult(supportedMethod(), amount, PaymentStatus.COD_PENDING, null, "Thanh toán khi nhận hàng (COD).");
    }


    private static void validateOrderCode(String orderCode) {
        if (orderCode == null || !orderCode.matches("[A-Za-z0-9-]{1,30}")) {
            throw new IllegalArgumentException("Mã đơn hàng không hợp lệ.");
        }
    }
}
