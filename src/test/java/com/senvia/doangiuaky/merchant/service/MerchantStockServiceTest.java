package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.merchant.api.MerchantStockApi;
import com.senvia.doangiuaky.merchant.api.MerchantStockException;
import com.senvia.doangiuaky.merchant.entity.Product;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.StockRestorationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Transactional
class MerchantStockServiceTest {

    @Autowired private MerchantStockApi stockApi;
    @Autowired private ProductRepository products;
    @Autowired private StockRestorationRepository restorations;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;

    @Test
    void deductsConditionallyAndExposesNewStockInSameTransaction() {
        Product product = products.findByName("Canvas Tote Bag").orElseThrow();
        int before = product.getStockQuantity();

        stockApi.deductForCheckout(product.getShopId(), product.getId(), 2, product.getPrice());

        assertEquals(before - 2, product.getStockQuantity());
        assertEquals(before - 2, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void rejectsInsufficientStockChangedPriceAndWrongShop() {
        Product product = products.findByName("Canvas Tote Bag").orElseThrow();
        assertThrows(MerchantStockException.class, () -> stockApi.deductForCheckout(
                product.getShopId(), product.getId(), product.getStockQuantity() + 1, product.getPrice()));
        assertThrows(MerchantStockException.class, () -> stockApi.deductForCheckout(
                product.getShopId(), product.getId(), 1, product.getPrice().add(BigDecimal.ONE)));
        assertThrows(MerchantStockException.class, () -> stockApi.deductForCheckout(
                Long.MAX_VALUE, product.getId(), 1, product.getPrice()));
        assertEquals(45, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    void rejectsHiddenProductOrInactiveCategory() {
        Product product = products.findByName("Canvas Tote Bag").orElseThrow();
        product.hide();
        products.saveAndFlush(product);
        assertThrows(MerchantStockException.class, () -> stockApi.deductForCheckout(
                product.getShopId(), product.getId(), 1, product.getPrice()));

        product.show();
        products.saveAndFlush(product);
        jdbc.update("update categories set active = false where id = ?", product.getCategoryId());
        entityManager.clear();
        assertThrows(MerchantStockException.class, () -> stockApi.deductForCheckout(
                product.getShopId(), product.getId(), 1, product.getPrice()));
    }

    @Test
    void rejectsLockedShopAndOwner() {
        Product product = products.findByName("Canvas Tote Bag").orElseThrow();
        jdbc.update("""
                update shops set status = 'LOCKED', lock_reason = 'test',
                    locked_by = owner_id, locked_at = current_timestamp where id = ?
                """, product.getShopId());
        entityManager.clear();
        assertThrows(MerchantStockException.class, () -> stockApi.deductForCheckout(
                product.getShopId(), product.getId(), 1, product.getPrice()));

        jdbc.update("update shops set status = 'APPROVED', lock_reason = null, locked_by = null, locked_at = null where id = ?",
                product.getShopId());
        jdbc.update("""
                update users set account_status = 'LOCKED', lock_reason = 'test',
                    locked_by = id, locked_at = current_timestamp
                    where id = (select owner_id from shops where id = ?)
                """, product.getShopId());
        entityManager.clear();
        assertThrows(MerchantStockException.class, () -> stockApi.deductForCheckout(
                product.getShopId(), product.getId(), 1, product.getPrice()));
    }

    @Test
    void restorationIsIdempotentAndRejectsChangedPayloadEvenForHiddenProduct() {
        Product product = products.findByName("Canvas Tote Bag").orElseThrow();
        int before = product.getStockQuantity();
        product.hide();
        products.saveAndFlush(product);

        stockApi.restoreCancelledOrderItem(Long.MAX_VALUE, product.getShopId(), product.getId(), 3);
        stockApi.restoreCancelledOrderItem(Long.MAX_VALUE, product.getShopId(), product.getId(), 3);

        assertEquals(before + 3, product.getStockQuantity());
        assertThrows(MerchantStockException.class, () -> stockApi.restoreCancelledOrderItem(
                Long.MAX_VALUE, product.getShopId(), product.getId(), 4));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void stockCommandsRequireCallerTransactionAndRollbackWithIt() {
        Product product = products.findByName("Canvas Tote Bag").orElseThrow();
        assertThrows(IllegalTransactionStateException.class, () -> stockApi.deductForCheckout(
                product.getShopId(), product.getId(), 1, product.getPrice()));

        int before = product.getStockQuantity();
        assertThrows(IllegalStateException.class, () -> transactionTemplate.execute(status -> {
            stockApi.deductForCheckout(product.getShopId(), product.getId(), 1, product.getPrice());
            throw new IllegalStateException("ordering failed");
        }));
        assertEquals(before, products.findById(product.getId()).orElseThrow().getStockQuantity());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void restorationLedgerAndStockRollBackTogether() {
        Product product = products.findByName("Canvas Tote Bag").orElseThrow();
        int before = product.getStockQuantity();
        long orderItemId = Long.MAX_VALUE - 1;

        assertThrows(IllegalStateException.class, () -> transactionTemplate.execute(status -> {
            stockApi.restoreCancelledOrderItem(orderItemId, product.getShopId(), product.getId(), 2);
            throw new IllegalStateException("cancellation failed");
        }));

        assertEquals(before, products.findById(product.getId()).orElseThrow().getStockQuantity());
        assertTrue(restorations.findById(orderItemId).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCheckoutCannotOversell() throws Exception {
        Product seed = products.findByName("Canvas Tote Bag").orElseThrow();
        Product testProduct = transactionTemplate.execute(status -> products.saveAndFlush(Product.create(
                seed.getShopId(), seed.getCategoryId(), "Concurrent stock test",
                "Concurrent checkout stock test", new BigDecimal("10.00"), 5)));
        assertTrue(testProduct != null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> checkout = () -> {
                start.await();
                try {
                    transactionTemplate.execute(status -> {
                        stockApi.deductForCheckout(testProduct.getShopId(), testProduct.getId(),
                                3, testProduct.getPrice());
                        return null;
                    });
                    return true;
                } catch (MerchantStockException exception) {
                    return false;
                }
            };
            Future<Boolean> first = executor.submit(checkout);
            Future<Boolean> second = executor.submit(checkout);
            start.countDown();
            int success = (first.get() ? 1 : 0) + (second.get() ? 1 : 0);
            assertEquals(1, success);
            assertEquals(2, products.findById(testProduct.getId()).orElseThrow().getStockQuantity());
        } finally {
            transactionTemplate.execute(status -> {
                products.deleteById(testProduct.getId());
                return null;
            });
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRetryRestoresOneOrderItemOnlyOnce() throws Exception {
        Product seed = products.findByName("Canvas Tote Bag").orElseThrow();
        Product testProduct = transactionTemplate.execute(status -> products.saveAndFlush(Product.create(
                seed.getShopId(), seed.getCategoryId(), "Concurrent restore test",
                "Concurrent cancellation stock test", new BigDecimal("10.00"), 5)));
        assertTrue(testProduct != null);
        long orderItemId = Long.MAX_VALUE - 2;
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            java.util.concurrent.Callable<Void> restore = () -> {
                start.await();
                transactionTemplate.execute(status -> {
                    stockApi.restoreCancelledOrderItem(orderItemId, testProduct.getShopId(),
                            testProduct.getId(), 2);
                    return null;
                });
                return null;
            };
            Future<Void> first = executor.submit(restore);
            Future<Void> second = executor.submit(restore);
            start.countDown();
            first.get();
            second.get();
            assertEquals(7, products.findById(testProduct.getId()).orElseThrow().getStockQuantity());
            assertTrue(restorations.existsById(orderItemId));
        } finally {
            transactionTemplate.execute(status -> {
                restorations.deleteById(orderItemId);
                products.deleteById(testProduct.getId());
                return null;
            });
        }
    }
}
