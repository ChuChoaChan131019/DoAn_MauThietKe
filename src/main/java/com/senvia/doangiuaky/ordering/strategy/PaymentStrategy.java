package com.senvia.doangiuaky.ordering.strategy;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import java.math.BigDecimal;

public interface PaymentStrategy {
    PaymentMethod supportedMethod();

    PaymentResult initialize(String orderCode, BigDecimal amount);
}
