package com.senvia.doangiuaky.ordering.strategy;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class PaymentStrategyFactory {
    private final Map<PaymentMethod, PaymentStrategy> strategies;

    public PaymentStrategyFactory(List<PaymentStrategy> availableStrategies) {
        Objects.requireNonNull(availableStrategies, "availableStrategies");

        EnumMap<PaymentMethod, PaymentStrategy> map = new EnumMap<>(PaymentMethod.class);

        for (PaymentStrategy strategy : availableStrategies) {
            Objects.requireNonNull(strategy, "PaymentStrategy must not be null");

            PaymentMethod method = Objects.requireNonNull(strategy.supportedMethod(), "Strategy must have a payment method");

            if (map.putIfAbsent(method, strategy) != null) {
                throw new IllegalStateException("Trùng PaymentStrategy cho phương thức: " + method);
            }
        }
        this.strategies = Map.copyOf(map);
    }

    public PaymentStrategy getStrategy(PaymentMethod method) {
        if (method == null) {
            throw new IllegalArgumentException("Vui lòng chọn phương thức thanh toán.");
        }

        PaymentStrategy strategy = strategies.get(method);

        if (strategy == null) {
            throw new IllegalArgumentException("Phương thức thanh toán chưa được hỗ trợ: " + method);
        }
        return strategy;
    }
}