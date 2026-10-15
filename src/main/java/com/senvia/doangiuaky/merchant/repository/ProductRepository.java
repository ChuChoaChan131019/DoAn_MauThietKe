package com.senvia.doangiuaky.merchant.repository;

import com.senvia.doangiuaky.merchant.entity.Product;
import com.senvia.doangiuaky.merchant.entity.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByIdAsc();

    List<Product> findAllByStatusAndStockQuantityGreaterThanOrderByIdAsc(
            ProductStatus status, int stockQuantity);

    @Query("select p from Product p "
            + "where p.status = :status and p.stockQuantity > :stockQuantity "
            + "and (:filterByCategory = false or p.categoryId in :categoryIds) "
            + "and (:filterByMinPrice = false or p.price >= :minPrice) "
            + "and (:filterByMaxPrice = false or p.price <= :maxPrice) "
            + "and (:filterByQuery = false or lower(p.name) like concat('%', lower(:query), '%') escape '!') "
            + "order by p.id asc")
    List<Product> findCatalogCandidates(
            @Param("status") ProductStatus status,
            @Param("stockQuantity") int stockQuantity,
            @Param("filterByCategory") boolean filterByCategory,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("filterByMinPrice") boolean filterByMinPrice,
            @Param("minPrice") BigDecimal minPrice,
            @Param("filterByMaxPrice") boolean filterByMaxPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("filterByQuery") boolean filterByQuery,
            @Param("query") String query);

    List<Product> findAllByShopIdOrderByIdAsc(Long shopId);

    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    long countByShopId(Long shopId);

    Optional<Product> findByName(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :productId")
    Optional<Product> findByIdForUpdate(@Param("productId") Long productId);

    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity - :quantity "
            + "where p.id = :productId and p.shopId = :shopId "
            + "and p.stockQuantity >= :quantity and p.price = :price and p.status = :status")
    int deductAvailableStock(@Param("productId") Long productId,
                             @Param("shopId") Long shopId,
                             @Param("quantity") int quantity,
                             @Param("price") BigDecimal price,
                             @Param("status") ProductStatus status);

    @Modifying
    @Query("update Product p set p.stockQuantity = p.stockQuantity + :quantity "
            + "where p.id = :productId and p.shopId = :shopId")
    int restoreStock(@Param("productId") Long productId,
                     @Param("shopId") Long shopId,
                     @Param("quantity") int quantity);
}
