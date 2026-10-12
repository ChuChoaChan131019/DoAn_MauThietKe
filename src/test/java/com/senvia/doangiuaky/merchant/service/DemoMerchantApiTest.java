package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class DemoMerchantApiTest {

    @Autowired
    private DemoMerchantApi merchantApi;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Test
    void returnsSaleableDatabaseProductThroughPublicContract() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();

        var product = merchantApi.findProductForCart(productId);

        assertTrue(product.isPresent());
        assertEquals("Canvas Tote Bag", product.orElseThrow().productName());
        assertTrue(product.orElseThrow().isSaleable());
    }

    @Test
    void returnsExistingOutOfStockProductAsUnavailableSummary() {
        Long productId = productRepository.findByName("Linen Shirt").orElseThrow().getId();

        var product = merchantApi.findProductForCart(productId);

        assertTrue(product.isPresent());
        assertEquals(0, product.orElseThrow().stockQuantity());
        assertFalse(product.orElseThrow().isSaleable());
    }

    @Test
    void returnsEmptyForUnknownProduct() {
        assertTrue(merchantApi.findProductForCart(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void lockedOwnerMakesExistingProductUnavailableWithoutHidingItsSummary() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        Long shopId = jdbcTemplate.queryForObject(
                "select shop_id from products where id = ?", Long.class, productId);

        jdbcTemplate.update("""
                update users
                   set account_status = 'LOCKED',
                       lock_reason = 'saleability test',
                       locked_by = id,
                       locked_at = CURRENT_TIMESTAMP
                 where id = (select owner_id from shops where id = ?)
                """, shopId);
        entityManager.clear();

        var product = merchantApi.findProductForCart(productId).orElseThrow();

        assertFalse(product.ownerActive());
        assertFalse(product.isSaleable());
    }

    @ParameterizedTest(name = "shop status {0} is not saleable")
    @ValueSource(strings = {"PENDING", "REJECTED", "LOCKED"})
    void nonApprovedShopMakesExistingProductUnavailable(String shopStatus) {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        Long shopId = jdbcTemplate.queryForObject(
                "select shop_id from products where id = ?", Long.class, productId);

        if ("LOCKED".equals(shopStatus)) {
            Long ownerId = jdbcTemplate.queryForObject(
                    "select owner_id from shops where id = ?", Long.class, shopId);
            jdbcTemplate.update("""
                    update shops
                       set status = 'LOCKED',
                           lock_reason = 'saleability test',
                           locked_by = ?,
                           locked_at = CURRENT_TIMESTAMP
                     where id = ?
                    """, ownerId, shopId);
        } else {
            jdbcTemplate.update("update shops set status = ? where id = ?", shopStatus, shopId);
        }
        entityManager.clear();

        var product = merchantApi.findProductForCart(productId).orElseThrow();

        assertFalse(product.shopApproved());
        assertFalse(product.isSaleable());
    }

    @Test
    void inactiveCategoryMakesExistingProductUnavailable() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        Long categoryId = jdbcTemplate.queryForObject(
                "select category_id from products where id = ?", Long.class, productId);

        jdbcTemplate.update("update categories set active = false where id = ?", categoryId);
        entityManager.clear();

        var product = merchantApi.findProductForCart(productId).orElseThrow();

        assertFalse(product.categoryActive());
        assertFalse(product.isSaleable());
    }

    @Test
    void hiddenProductMakesExistingProductUnavailable() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();

        jdbcTemplate.update("update products set status = 'HIDDEN' where id = ?", productId);
        entityManager.clear();

        var product = merchantApi.findProductForCart(productId).orElseThrow();

        assertFalse(product.productActive());
        assertFalse(product.isSaleable());
    }

    @Test
    void catalogListAndDetailExcludeUnavailableProduct() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();

        jdbcTemplate.update("update products set status = 'HIDDEN' where id = ?", productId);
        entityManager.clear();

        assertTrue(merchantApi.findSaleableCatalogProducts().stream()
                .noneMatch(product -> product.id().equals(productId)));
        assertTrue(merchantApi.findSaleableCatalogProduct(productId).isEmpty());
    }
}
