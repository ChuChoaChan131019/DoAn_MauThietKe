package com.senvia.doangiuaky.ordering.api;

public interface AdminOrderApi {
    OrderPage listOrders(String status, Long buyerId, Long shopId, String orderCodeQuery, int page, int size);
    OrderDetails getOrder(String orderCode);
}
