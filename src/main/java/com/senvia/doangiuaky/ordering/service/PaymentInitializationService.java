package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.strategy.PaymentResult;
import com.senvia.doangiuaky.ordering.strategy.PaymentStrategyFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class PaymentInitializationService implements OrderDraftWriter.PaymentSeedProvider {
    private final PaymentStrategyFactory factory;

    public PaymentInitializationService(PaymentStrategyFactory factory) {
        this.factory = factory;
    }

    public PaymentResult preparePayment(PaymentMethod method, String orderCode, BigDecimal amount) {
        PaymentResult result = Objects.requireNonNull(factory.getStrategy(method).initialize(orderCode, amount), "PaymentStrategy must return a result");

        if (result.method() != method || result.amount().compareTo(amount) != 0) {
            throw new IllegalStateException("PaymentStrategy trả về phương thức " + "hoặc số tiền không khớp đơn hàng.");
        }
        return result;
    }

    @Override
    public OrderDraftWriter.PaymentSeed initialize(PaymentMethod method, String orderCode, BigDecimal amount) {
        PaymentResult result = preparePayment(method, orderCode, amount);

        return new OrderDraftWriter.PaymentSeed(result.status(), result.transactionReference());
    }
}