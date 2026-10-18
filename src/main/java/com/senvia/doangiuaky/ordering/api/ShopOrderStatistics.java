package com.senvia.doangiuaky.ordering.api;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ShopOrderStatistics(Long shopId, long totalOrders, Map<String, Long> ordersByStatus, BigDecimal completedRevenue) {
    public ShopOrderStatistics {
        ordersByStatus = Collections.unmodifiableMap(new LinkedHashMap<>(ordersByStatus));
    }
}