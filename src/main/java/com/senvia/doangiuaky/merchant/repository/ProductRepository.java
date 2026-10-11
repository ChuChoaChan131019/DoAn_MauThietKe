package com.senvia.doangiuaky.merchant.repository;

import com.senvia.doangiuaky.merchant.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findAllByOrderByIdAsc();

    List<Product> findAllByShopIdOrderByIdAsc(Long shopId);

    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    Optional<Product> findByName(String name);
}
