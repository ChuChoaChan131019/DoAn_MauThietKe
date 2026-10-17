package com.senvia.doangiuaky.ordering.repository;

import com.senvia.doangiuaky.ordering.entity.OrderStatusHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findAllByOrderIdOrderByChangedAtAscIdAsc(Long orderId);

    long countByOrderId(Long orderId);
}