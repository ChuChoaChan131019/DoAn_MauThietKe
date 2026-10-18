package com.senvia.doangiuaky.ordering.api;

public interface BuyerOrderApi {
    OrderPage listOwnOrders(String status, int page, int size);
    OrderDetails getOwnOrder(String orderCode);
}
