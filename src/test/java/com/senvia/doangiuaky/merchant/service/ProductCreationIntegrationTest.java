package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.dto.ProductForm;
import com.senvia.doangiuaky.merchant.dto.SellerProductView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.entity.ProductStatus;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class ProductCreationIntegrationTest {

    @Autowired
    private ProductManagementService productService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createsActiveProductForTheOwnerShopAndPersistsBackendOwnership() {
        Fixture fixture = fixture(ShopStatus.APPROVED);
        Category category = categoryRepository.saveAndFlush(
                Category.create("Creation category " + fixture.suffix(), null));

        SellerProductView result = productService.create(fixture.ownerId(), new ProductForm(
                "  Integration product  ", "Product description", category.getId(),
                new BigDecimal("25.50"), 7));

        var product = productRepository.findById(result.id()).orElseThrow();
        assertEquals(fixture.shopId(), product.getShopId());
        assertEquals(category.getId(), product.getCategoryId());
        assertEquals("Integration product", product.getName());
        assertEquals("Product description", product.getDescription());
        assertEquals(new BigDecimal("25.50"), product.getPrice());
        assertEquals(7, product.getStockQuantity());
        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        assertTrue(productRepository.findAllByShopIdOrderByIdAsc(fixture.shopId()).contains(product));
    }

    @ParameterizedTest
    @EnumSource(value = ShopStatus.class, names = {"PENDING", "REJECTED", "LOCKED"})
    void nonApprovedShopCannotCreateProduct(ShopStatus status) {
        Fixture fixture = fixture(status);
        Category category = categoryRepository.saveAndFlush(
                Category.create("Blocked category " + fixture.suffix(), null));

        assertThrows(ProductAuthorizationException.class,
                () -> productService.create(fixture.ownerId(), new ProductForm(
                        "Blocked product", null, category.getId(), new BigDecimal("10.00"), 1)));

        assertEquals(0, productRepository.findAllByShopIdOrderByIdAsc(fixture.shopId()).size());
    }

    private Fixture fixture(ShopStatus status) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "product-create-" + suffix + "@example.test";
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status,
                                   created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Product Creation User", email, "test-password-hash");
        Long ownerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, email);

        ShopStatus persistedStatus = status == ShopStatus.LOCKED ? ShopStatus.APPROVED : status;
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   created_at, updated_at)
                values (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, "Product creation shop " + suffix, "0900000000", "Test address",
                persistedStatus.name());
        Long shopId = jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);

        if (status == ShopStatus.LOCKED) {
            jdbcTemplate.update("""
                    update shops
                    set status = 'LOCKED', lock_reason = ?, locked_by = ?, locked_at = CURRENT_TIMESTAMP
                    where id = ?
                    """, "Test lock", ownerId, shopId);
        }
        return new Fixture(ownerId, shopId, suffix);
    }

    private record Fixture(Long ownerId, Long shopId, String suffix) {
    }
}
