package com.senvia.doangiuaky.merchant.repository;

import com.senvia.doangiuaky.merchant.entity.Category;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class CategoryRepositoryTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void persistsCategoryAndFindsOnlyActiveCategories() {
        Category active = categoryRepository.saveAndFlush(Category.create("Active repository category", null));
        Category hidden = categoryRepository.saveAndFlush(Category.create("Hidden repository category", null));
        hidden.hide();
        categoryRepository.saveAndFlush(hidden);

        assertTrue(categoryRepository.findById(active.getId()).isPresent());
        assertTrue(categoryRepository.findAllByActiveTrueOrderByNameAscIdAsc().stream()
                .anyMatch(category -> category.getId().equals(active.getId())));
        assertFalse(categoryRepository.findAllByActiveTrueOrderByNameAscIdAsc().stream()
                .anyMatch(category -> category.getId().equals(hidden.getId())));
    }

    @Test
    void supportsDuplicateChecksForCreateAndUpdate() {
        Category first = categoryRepository.saveAndFlush(Category.create("Unique repository category", null));
        Category second = categoryRepository.saveAndFlush(Category.create("Another repository category", null));

        assertTrue(categoryRepository.existsByName(first.getName()));
        assertTrue(categoryRepository.existsByNameAndIdNot(first.getName(), second.getId()));
        assertFalse(categoryRepository.existsByNameAndIdNot(first.getName(), first.getId()));
    }

    @Test
    void databaseRejectsExactDuplicateNames() {
        categoryRepository.saveAndFlush(Category.create("Database unique category", null));

        assertThrows(DataIntegrityViolationException.class,
                () -> categoryRepository.saveAndFlush(Category.create("Database unique category", null)));
    }
}
