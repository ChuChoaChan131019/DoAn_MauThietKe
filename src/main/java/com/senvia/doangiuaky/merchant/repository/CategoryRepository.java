package com.senvia.doangiuaky.merchant.repository;

import com.senvia.doangiuaky.merchant.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAscIdAsc();

    List<Category> findAllByActiveTrueOrderByNameAscIdAsc();

    Optional<Category> findByIdAndActiveTrue(Long id);

    Optional<Category> findByNameAndActiveTrue(String name);

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
