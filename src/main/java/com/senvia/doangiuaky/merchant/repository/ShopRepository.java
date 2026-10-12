package com.senvia.doangiuaky.merchant.repository;

import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Optional<Shop> findByOwnerId(Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shop from Shop shop where shop.ownerId = :ownerId")
    Optional<Shop> findByOwnerIdForUpdate(@Param("ownerId") Long ownerId);

    boolean existsByOwnerId(Long ownerId);

    List<Shop> findAllByStatusOrderBySubmittedAtAsc(ShopStatus status);

    List<Shop> findAllByStatusInOrderByShopNameAscIdAsc(Set<ShopStatus> statuses);

    Optional<Shop> findByIdAndStatus(Long id, ShopStatus status);

    Optional<Shop> findByIdAndStatusIn(Long id, Set<ShopStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shop from Shop shop where shop.id = :shopId")
    Optional<Shop> findByIdForUpdate(@Param("shopId") Long shopId);
}
