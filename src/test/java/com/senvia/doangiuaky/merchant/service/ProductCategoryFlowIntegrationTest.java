package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.dto.ProductForm;
import com.senvia.doangiuaky.merchant.dto.SellerProductView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class ProductCategoryFlowIntegrationTest {

    @Autowired
    private ProductManagementService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void productFlowRejectsHiddenCategoriesAndAllowsOwnerToMoveExistingProduct() {
        Fixture fixture = fixture();
        Category oldCategory = categoryRepository.saveAndFlush(
                Category.create("Old category " + fixture.suffix(), null));
        Category targetCategory = categoryRepository.saveAndFlush(
                Category.create("Target category " + fixture.suffix(), null));

        SellerProductView created = productService.create(fixture.ownerId(), form(oldCategory.getId()));

        oldCategory.hide();
        categoryRepository.saveAndFlush(oldCategory);

        assertThrows(InactiveCategoryException.class,
                () -> productService.create(fixture.ownerId(), form(oldCategory.getId())));
        assertThrows(InactiveCategoryException.class,
                () -> productService.update(fixture.ownerId(), created.id(), form(oldCategory.getId())));

        SellerProductView moved = productService.update(
                fixture.ownerId(), created.id(), form(targetCategory.getId()));

        assertEquals(targetCategory.getId(), moved.categoryId());
        assertEquals(targetCategory.getId(), productRepository.findById(created.id()).orElseThrow().getCategoryId());
    }

    private ProductForm form(Long categoryId) {
        return new ProductForm(
                "Integration product",
                "Product category flow",
                categoryId,
                new BigDecimal("12.50"),
                4);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "product-flow-" + suffix + "@example.test";
        String shopName = "Product flow shop " + suffix;

        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status,
                                   created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Product Flow User", email, "test-password-hash");
        Long ownerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, email);

        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   created_at, updated_at)
                values (?, ?, ?, ?, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName, "0900000000", "Test address");

        return new Fixture(ownerId, suffix);
    }

    private record Fixture(Long ownerId, String suffix) {
    }
}
