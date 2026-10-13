package com.senvia.doangiuaky.shopping.repository;

import com.senvia.doangiuaky.shopping.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    Optional<CartItem> findByIdAndCartId(Long id, Long cartId);

    List<CartItem> findAllByCartIdOrderByIdAsc(Long cartId);
}
