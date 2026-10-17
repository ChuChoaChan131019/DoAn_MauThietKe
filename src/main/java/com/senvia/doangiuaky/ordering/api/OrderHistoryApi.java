package com.senvia.doangiuaky.ordering.api;

import java.util.List;

public interface OrderHistoryApi {
    List<OrderHistoryEntry> forCurrentBuyer(String orderCode);
    List<OrderHistoryEntry> forCurrentSeller(String orderCode);
    List<OrderHistoryEntry> forCurrentAdmin(String orderCode);
}