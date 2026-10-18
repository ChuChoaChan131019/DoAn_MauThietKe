package com.senvia.doangiuaky.engagement.repository;

import com.senvia.doangiuaky.engagement.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItemId(Long orderItemId);

    Optional<Review> findByOrderItemId(Long orderItemId);

    Page<Review> findAllByProductIdOrderByCreatedAtDescIdDesc(Long productId, Pageable pageable);

    long countByProductId(Long productId);

    @Query("select avg(review.rating) from Review review where review.productId = :productId")
    Double averageRatingForProduct(@Param("productId") Long productId);
}
