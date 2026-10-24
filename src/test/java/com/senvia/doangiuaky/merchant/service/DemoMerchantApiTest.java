package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.dto.CatalogFilterForm;
import com.senvia.doangiuaky.merchant.entity.ProductImage;
import com.senvia.doangiuaky.merchant.repository.ProductImageRepository;
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
    private ProductImageRepository productImageRepository;

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
    void shopSummaryExposesOnlyIdentityAndVisibilityData() {
        Long shopId = shopIdOf("Canvas Tote Bag");

        var summary = merchantApi.findShopSummary(shopId).orElseThrow();
        var ownerSummary = merchantApi.findShopSummaryForOwner(summary.ownerId()).orElseThrow();

        assertEquals(shopId, summary.shopId());
        assertEquals("NOLA Store", summary.shopName());
        assertTrue(summary.ownerActive());
        assertTrue(summary.shopApproved());
        assertEquals(summary, ownerSummary);
        assertTrue(merchantApi.findShopSummaryForOwner(null).isEmpty());
        assertTrue(merchantApi.findShopSummaryForOwner(Long.MAX_VALUE).isEmpty());
        assertTrue(merchantApi.findShopSummary(Long.MAX_VALUE).isEmpty());
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
    void catalogFilterUsesInclusiveExactDecimalBoundsAndCombinesCategoryAndQuery() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        Long categoryId = jdbcTemplate.queryForObject(
                "select category_id from products where id = ?", Long.class, productId);
        jdbcTemplate.update("update products set price = ? where id = ?",
                new java.math.BigDecimal("1190000.25"), productId);
        entityManager.clear();

        CatalogFilterForm filter = new CatalogFilterForm();
        filter.setCategoryId(categoryId);
        filter.setMin(new java.math.BigDecimal("1190000.25"));
        filter.setMax(new java.math.BigDecimal("1190000.25"));
        filter.setQ("  CANVAS  ");

        var result = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "newest");

        assertTrue(result.categoryAvailable());
        assertEquals(1, result.products().size());
        assertEquals(productId, result.products().getFirst().id());
        assertEquals("Canvas Tote Bag", result.products().getFirst().name());
    }

    @Test
    void catalogSearchTrimsQueryIgnoresCaseAndTreatsLikeWildcardsLiterally() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        jdbcTemplate.update("update products set name = ? where id = ?",
                "Canvas 100% Cotton_Bag!", productId);
        entityManager.clear();

        CatalogFilterForm filter = new CatalogFilterForm();
        filter.setQ("  cAnVaS  ");
        var partialMatch = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "newest");
        assertTrue(partialMatch.products().stream().anyMatch(product -> product.id().equals(productId)));

        filter.setQ("%");
        var percentMatch = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "newest");
        assertEquals(1, percentMatch.products().size());
        assertEquals(productId, percentMatch.products().getFirst().id());

        filter.setQ("_");
        var underscoreMatch = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "newest");
        assertEquals(1, underscoreMatch.products().size());
        assertEquals(productId, underscoreMatch.products().getFirst().id());

        filter.setQ("!");
        var escapeCharacterMatch = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "newest");
        assertEquals(1, escapeCharacterMatch.products().size());
        assertEquals(productId, escapeCharacterMatch.products().getFirst().id());

        filter.setQ("   ");
        var blankQuery = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "newest");
        assertTrue(blankQuery.products().size() > 1);
    }

    @Test
    void catalogFilterDoesNotReturnProductsForInactiveOrUnknownCategory() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        Long categoryId = jdbcTemplate.queryForObject(
                "select category_id from products where id = ?", Long.class, productId);
        jdbcTemplate.update("update categories set active = false where id = ?", categoryId);
        entityManager.clear();

        CatalogFilterForm filter = new CatalogFilterForm();
        filter.setCategoryId(categoryId);

        var inactiveResult = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "newest");
        filter.setCategoryId(Long.MAX_VALUE);
        var unknownResult = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "newest");

        assertFalse(inactiveResult.categoryAvailable());
        assertTrue(inactiveResult.products().isEmpty());
        assertFalse(unknownResult.categoryAvailable());
        assertTrue(unknownResult.products().isEmpty());
    }

    @Test
    void catalogPagesOneThousandMatchingProductsWithStablePriceTies() {
        Long productId = productRepository.findByName("Canvas Tote Bag").orElseThrow().getId();
        Long shopId = jdbcTemplate.queryForObject(
                "select shop_id from products where id = ?", Long.class, productId);
        Long categoryId = jdbcTemplate.queryForObject(
                "select category_id from products where id = ?", Long.class, productId);
        for (int index = 0; index < 1_000; index++) {
            jdbcTemplate.update("""
                    insert into products (shop_id, category_id, name, description, price, stock_quantity, status)
                    values (?, ?, ?, 'Pagination test product', 100000.00, 1, 'ACTIVE')
                    """, shopId, categoryId, "Pagination-Scale-" + index);
        }
        entityManager.clear();

        CatalogFilterForm filter = new CatalogFilterForm();
        filter.setQ("Pagination-Scale-");
        var first = merchantApi.filterSaleableCatalogProducts(filter, 1, 12, "price_asc");
        var second = merchantApi.filterSaleableCatalogProducts(filter, 2, 12, "price_asc");
        var last = merchantApi.filterSaleableCatalogProducts(filter, 999, 12, "price_asc");

        assertEquals(1_000, first.totalItems());
        assertEquals(84, first.totalPages());
        assertEquals(12, first.products().size());
        assertEquals(12, second.products().size());
        assertEquals(84, last.currentPage());
        assertEquals(4, last.products().size());
        assertTrue(first.products().getLast().id() < second.products().getFirst().id());
        assertTrue(second.products().getLast().id() < last.products().getFirst().id());
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
        assertTrue(detail.thumbnailUrl().startsWith("https://"));
        assertEquals(1, detail.images().size());
        assertEquals(detail.thumbnailUrl(), detail.images().getFirst().imageUrl());
        assertEquals(detail.shopId(), detail.shop().id());
    }

    @Test
    void catalogImagesFollowDisplayOrderAndFirstImageBecomesThumbnail() {
        var product = productRepository.findByName("Canvas Tote Bag").orElseThrow();
        var seededImage = productImageRepository
                .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId()).getFirst();
        productImageRepository.saveAndFlush(ProductImage.create(
                product, "https://example.test/second.jpg", "test/second", 2));
        productImageRepository.saveAndFlush(ProductImage.create(
                product, "https://example.test/first.jpg", "test/first", 1));

        var detail = merchantApi.findSaleableCatalogProduct(product.getId()).orElseThrow();
        var card = merchantApi.findSaleableCatalogProducts().stream()
                .filter(candidate -> candidate.id().equals(product.getId()))
                .findFirst().orElseThrow();

        assertEquals(seededImage.getImageUrl(), detail.thumbnailUrl());
        assertEquals(seededImage.getImageUrl(), card.thumbnailUrl());
        assertEquals(3, detail.images().size());
        assertEquals(seededImage.getImageUrl(), detail.images().get(0).imageUrl());
        assertEquals("https://example.test/first.jpg", detail.images().get(1).imageUrl());
        assertEquals("https://example.test/second.jpg", detail.images().get(2).imageUrl());
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
