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
import org.springframework.transaction.annotation.Propagation;

import org.springframework.transaction.IllegalTransactionStateException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void cartMutationQueryReturnsLockedProductInsideCallerTransaction() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();

        var product = merchantApi.findProductForCartMutation(productId);

        assertTrue(product.isPresent());
        assertEquals(productId, product.orElseThrow().productId());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cartMutationQueryRequiresCallerTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                () -> merchantApi.findProductForCartMutation(1L));
    }

    @Test
    void returnsEmptyCapabilitiesForInvalidOrUnknownShop() {
        assertTrue(merchantApi.findShopOrderCapabilities(null).isEmpty());
        assertTrue(merchantApi.findShopOrderCapabilities(0L).isEmpty());
        assertTrue(merchantApi.findShopOrderCapabilities(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void approvedShopCanReceiveAndHandleOrders() {
        Long shopId = shopIdOf("Canvas Tote Bag");

        var capabilities = merchantApi.findShopOrderCapabilities(shopId).orElseThrow();

        assertEquals(shopId, capabilities.shopId());
        assertTrue(capabilities.canReceiveNewOrders());
        assertTrue(capabilities.canHandleExistingOrders());
    }

    @Test
    void lockedShopCannotReceiveNewOrdersButCanHandleExistingOrders() {
        Long shopId = shopIdOf("Canvas Tote Bag");
        lockShop(shopId);

        var capabilities = merchantApi.findShopOrderCapabilities(shopId).orElseThrow();

        assertFalse(capabilities.canReceiveNewOrders());
        assertTrue(capabilities.canHandleExistingOrders());
    }

    @ParameterizedTest(name = "shop status {0} cannot receive or handle orders")
    @ValueSource(strings = {"PENDING", "REJECTED"})
    void unapprovedShopCannotReceiveOrHandleOrders(String shopStatus) {
        Long shopId = shopIdOf("Canvas Tote Bag");
        jdbcTemplate.update("update shops set status = ? where id = ?", shopStatus, shopId);
        entityManager.clear();

        var capabilities = merchantApi.findShopOrderCapabilities(shopId).orElseThrow();

        assertFalse(capabilities.canReceiveNewOrders());
        assertFalse(capabilities.canHandleExistingOrders());
    }

    @Test
    void lockedOwnerStopsNewOrdersWithoutChangingApprovedShopOldOrderCapability() {
        Long shopId = shopIdOf("Canvas Tote Bag");
        lockOwner(shopId);

        var capabilities = merchantApi.findShopOrderCapabilities(shopId).orElseThrow();

        assertFalse(capabilities.canReceiveNewOrders());
        assertTrue(capabilities.canHandleExistingOrders());
    }

    @Test
    void lockedOwnerMakesExistingProductUnavailableWithoutHidingItsSummary() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        Long shopId = shopIdOf("Canvas Tote Bag");
        lockOwner(shopId);

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

    @Test
    void catalogListAndDetailExcludeProductOfLockedShop() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        lockShop(shopIdOf("Canvas Tote Bag"));

        assertTrue(merchantApi.findSaleableCatalogProducts().stream()
                .noneMatch(product -> product.id().equals(productId)));
        assertTrue(merchantApi.findSaleableCatalogProduct(productId).isEmpty());
    }

    @Test
    void catalogDetailUsesDatabaseProductShopCategoryAndOrderedImages() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();

        var detail = merchantApi.findSaleableCatalogProduct(productId).orElseThrow();

        assertEquals(productId, detail.id());
        assertEquals("Canvas Tote Bag", detail.name());
        assertEquals("NOLA Store", detail.shopName());
        assertEquals("Thời trang", detail.categoryName());
        assertEquals(45, detail.stock());
        assertEquals("/images/product-placeholder.svg", detail.thumbnailUrl());
        assertEquals(1, detail.images().size());
        assertEquals(detail.thumbnailUrl(), detail.images().getFirst().imageUrl());
        assertEquals(detail.shopId(), detail.shop().id());
    }

    @Test
    void publicShopContainsOnlySaleableProductsFromThatShop() {
        Long shopId = shopIdOf("Canvas Tote Bag");

        var shop = merchantApi.findPublicShop(shopId).orElseThrow();
        var products = merchantApi.findSaleableCatalogProductsByShop(shopId);

        assertEquals(shopId, shop.id());
        assertEquals("NOLA Store", shop.name());
        assertTrue(products.stream().allMatch(product -> product.shopId().equals(shopId)));
        assertTrue(products.stream().anyMatch(product -> product.name().equals("Canvas Tote Bag")));
        assertTrue(products.stream().noneMatch(product -> product.name().equals("Linen Shirt")));
    }

    @Test
    void publicShopIsHiddenWhenOwnerIsLocked() {
        Long shopId = shopIdOf("Canvas Tote Bag");
        lockOwner(shopId);

        assertTrue(merchantApi.findPublicShop(shopId).isEmpty());
        assertTrue(merchantApi.findSaleableCatalogProductsByShop(shopId).isEmpty());
    }

    private Long shopIdOf(String productName) {
        Long productId = productRepository.findByName(productName).orElseThrow().getId();
        return jdbcTemplate.queryForObject(
                "select shop_id from products where id = ?", Long.class, productId);
    }

    private void lockShop(Long shopId) {
        Long ownerId = jdbcTemplate.queryForObject(
                "select owner_id from shops where id = ?", Long.class, shopId);
        jdbcTemplate.update("""
                update shops
                   set status = 'LOCKED',
                       lock_reason = 'capability test',
                       locked_by = ?,
                       locked_at = CURRENT_TIMESTAMP
                 where id = ?
                """, ownerId, shopId);
        entityManager.clear();
    }

    private void lockOwner(Long shopId) {
        jdbcTemplate.update("""
                update users
                   set account_status = 'LOCKED',
                       lock_reason = 'capability test',
                       locked_by = id,
                       locked_at = CURRENT_TIMESTAMP
                 where id = (select owner_id from shops where id = ?)
                """, shopId);
        entityManager.clear();
    }
}
