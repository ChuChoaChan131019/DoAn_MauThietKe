package com.senvia.doangiuaky.ordering.repository;

import com.senvia.doangiuaky.ordering.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(
        @Param("orderId") Long orderId
    );

    List<Order> findAllByBuyerIdOrderByCreatedAtDesc(Long buyerId);
    List<Order> findAllByShopIdOrderByCreatedAtDesc(Long shopId);
    List<Order> findAllByShopIdInOrderByCreatedAtDesc(List<Long> shopIds);

    @Query("select distinct o.shopId from Order o")
    List<Long> findDistinctShopIds();

    // ORDER 02 - Danh sách đơn theo shop, có phân trang.
    Page<Order> findByShopIdIn(Collection<Long> shopIds, Pageable pageable);

    // ORDER 02 - Lọc trạng thái và phân trang tại database.
    Page<Order> findByShopIdInAndStatus(Collection<Long> shopIds, OrderStatus status, Pageable pageable);

    // ORDER 02 - Đếm đơn theo shop và trạng thái.
    long countByShopIdInAndStatus(Collection<Long> shopIds, OrderStatus status);
}
