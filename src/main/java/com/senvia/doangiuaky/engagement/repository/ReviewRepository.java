package com.senvia.doangiuaky.engagement.repository;

import com.senvia.doangiuaky.engagement.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByOrderItemId(Long orderItemId);
}
