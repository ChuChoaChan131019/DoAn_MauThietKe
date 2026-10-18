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
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.math.BigDecimal;

public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {
    Optional<Order> findByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :orderId")
    Optional<Order> findByIdForUpdate(@Param("orderId") Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);

    List<Order> findAllByBuyerIdOrderByCreatedAtDesc(Long buyerId);
    List<Order> findAllByShopIdOrderByCreatedAtDesc(Long shopId);
    List<Order> findAllByShopIdInOrderByCreatedAtDesc(List<Long> shopIds);

    @Query("select distinct o.shopId from Order o")
    List<Long> findDistinctShopIds();

    // CONTRACT: Buyer order list, lọc tại database.
    Page<Order> findByBuyerId(Long buyerId, Pageable pageable);
    Page<Order> findByBuyerIdAndStatus(Long buyerId, OrderStatus status, Pageable pageable);

    // CONTRACT: Đếm đơn theo trạng thái của một shop.
    @Query("""
            select o.status, count(o)
            from Order o
            where o.shopId = :shopId
            group by o.status
            """)
    List<Object[]> countOrdersByShopAndStatus(@Param("shopId") Long shopId);

    // CONTRACT: Chỉ tính doanh thu theo shop và trạng thái.
    @Query("""
            select sum(o.totalAmount)
            from Order o
            where o.shopId = :shopId
              and o.status = :status
            """)
    BigDecimal sumAmountByShopAndStatus(@Param("shopId") Long shopId, @Param("status") OrderStatus status);

    // ORDER 02 - Danh sách đơn theo shop, có phân trang.
    Page<Order> findByShopIdIn(Collection<Long> shopIds, Pageable pageable);

    // ORDER 02 - Lọc trạng thái và phân trang tại database.
    Page<Order> findByShopIdInAndStatus(Collection<Long> shopIds, OrderStatus status, Pageable pageable);

    // ORDER 02 - Đếm đơn theo shop và trạng thái.
    long countByShopIdInAndStatus(Collection<Long> shopIds, OrderStatus status);
}
