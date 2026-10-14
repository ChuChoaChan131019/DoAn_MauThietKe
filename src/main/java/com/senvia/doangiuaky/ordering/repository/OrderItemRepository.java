package com.senvia.doangiuaky.ordering.repository;

import com.senvia.doangiuaky.ordering.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findAllByOrderIdOrderByIdAsc(Long orderId);
}