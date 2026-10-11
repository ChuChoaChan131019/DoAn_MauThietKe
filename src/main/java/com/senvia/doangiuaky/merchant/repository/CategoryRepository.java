package com.senvia.doangiuaky.merchant.repository;

import com.senvia.doangiuaky.merchant.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByOrderByNameAscIdAsc();

    List<Category> findAllByActiveTrueOrderByNameAscIdAsc();

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, Long id);
}
