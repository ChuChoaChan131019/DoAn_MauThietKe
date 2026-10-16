package com.senvia.doangiuaky.ordering.repository;

import com.senvia.doangiuaky.ordering.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderCode(String orderCode);
    List<Order> findAllByBuyerIdOrderByCreatedAtDesc(Long buyerId);
    List<Order> findAllByShopIdOrderByCreatedAtDesc(Long shopId);
    List<Order> findAllByShopIdInOrderByCreatedAtDesc(List<Long> shopIds);

    @Query("select distinct o.shopId from Order o")
    List<Long> findDistinctShopIds();
}
