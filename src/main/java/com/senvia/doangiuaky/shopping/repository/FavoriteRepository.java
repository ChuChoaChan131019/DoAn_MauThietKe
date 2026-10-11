package com.senvia.doangiuaky.shopping.repository;

import com.senvia.doangiuaky.shopping.entity.Favorite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndProductId(Long userId, Long productId);

    boolean existsByUserIdAndProductId(Long userId, Long productId);

    List<Favorite> findAllByUserIdOrderByCreatedAtDescIdDesc(Long userId);

    long deleteByUserIdAndProductId(Long userId, Long productId);
}
