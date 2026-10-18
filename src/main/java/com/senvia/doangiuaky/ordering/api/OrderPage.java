package com.senvia.doangiuaky.ordering.api;

import java.util.List;

public record OrderPage(List<OrderSummary> orders, int currentPage, int totalPages, long totalElements) {
    public OrderPage {
        orders = List.copyOf(orders);
    }
}