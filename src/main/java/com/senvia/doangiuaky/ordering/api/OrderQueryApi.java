package com.senvia.doangiuaky.ordering.api;

import java.util.List;

/** Truy vấn đơn hàng cho giao diện buyer/admin qua contract công khai của Ordering. */
public interface OrderQueryApi {
    List<OrderListEntry> listForCurrentBuyer();

    List<OrderListEntry> listForCurrentAdmin();

    OrderDetailEntry getBuyerOrder(String orderCode);

    OrderDetailEntry getAdminOrder(String orderCode);
}
