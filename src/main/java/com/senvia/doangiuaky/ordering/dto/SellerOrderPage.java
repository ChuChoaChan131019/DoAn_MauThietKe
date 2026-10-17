package com.senvia.doangiuaky.ordering.dto;

import java.util.List;

public record SellerOrderPage(List<SellerOrderView> orders, int currentPage, int totalPages, long totalElements,
        long pendingCount) {
    public SellerOrderPage {
        orders = List.copyOf(orders);
    }

    public static SellerOrderPage empty() {
        return new SellerOrderPage(List.of(), 1, 1, 0L, 0L);
    }

    public boolean hasPrevious() {
        return currentPage > 1;
    }

    public boolean hasNext() {
        return currentPage < totalPages;
    }
}