package com.senvia.doangiuaky.merchant.repository;

import com.senvia.doangiuaky.merchant.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findAllByProductIdOrderByDisplayOrderAscIdAsc(Long productId);

    @Query("select image from ProductImage image where image.product.id in :productIds "
            + "order by image.product.id asc, image.displayOrder asc, image.id asc")
    List<ProductImage> findCatalogImages(@Param("productIds") Collection<Long> productIds);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);
}
