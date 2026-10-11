package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class CategoryVisibilityIntegrationTest {

    @Autowired
    private CategoryManagementService categoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private DemoMerchantApi merchantApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void hidingCategoryKeepsProductButRemovesItFromPublicCatalog() {
        Long adminId = activeAdminId();
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        Long categoryId = jdbcTemplate.queryForObject(
                "select category_id from products where id = ?", Long.class, productId);

        categoryService.hide(adminId, categoryId);

        assertTrue(productRepository.findById(productId).isPresent());
        assertFalse(categoryRepository.findById(categoryId).orElseThrow().isActive());
        assertFalse(merchantApi.findProductForCart(productId).orElseThrow().isSaleable());
        assertTrue(merchantApi.findSaleableCatalogProduct(productId).isEmpty());
    }

    @Test
    void inactiveCategoryCannotBeSelectedForNewProduct() {
        Long categoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = 'Thời trang'", Long.class);
        Category category = categoryRepository.findById(categoryId).orElseThrow();
        category.hide();
        categoryRepository.saveAndFlush(category);

        assertThrowsInactiveCategory(categoryId);
    }

    private void assertThrowsInactiveCategory(Long categoryId) {
        org.junit.jupiter.api.Assertions.assertThrows(InactiveCategoryException.class,
                () -> categoryService.requireActiveCategory(categoryId));
    }

    private Long activeAdminId() {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Category Admin", "category-admin@example.com", "$2a$10$exampleHashForCategoryTest");
        return jdbcTemplate.queryForObject(
                "select id from users where email = 'category-admin@example.com'", Long.class);
    }
}
