package com.senvia.doangiuaky.shopping.service;

import com.senvia.doangiuaky.shopping.entity.Cart;
import com.senvia.doangiuaky.shopping.entity.CartItem;
import com.senvia.doangiuaky.shopping.repository.CartItemRepository;
import com.senvia.doangiuaky.shopping.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CartServiceConcurrencyTest {

    @Autowired
    private CartService cartService;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentFirstAddsCreateOneCartAndKeepBothQuantities() throws Exception {
        Long userId = insertUser();
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<?>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    cartService.addItem(userId, 1L, 1);
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        Cart cart = cartRepository.findByUserId(userId).orElseThrow();
        List<CartItem> items = cartItemRepository.findAllByCartIdOrderByIdAsc(cart.getId());

        assertEquals(1, cartRepository.countByUserId(userId));
        assertEquals(1, items.size());
        assertEquals(2, items.getFirst().getQuantity());
    }

    @Test
    void concurrentAddsOnExistingCartAreSerializedAndClampedToStock() throws Exception {
        Long userId = insertUser();
        Long productId = insertSaleableProduct(5);
        cartService.addItem(userId, productId, 1);
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> first = executor.submit(() -> {
                start.await();
                cartService.addItem(userId, productId, 4);
                return null;
            });
            Future<?> second = executor.submit(() -> {
                start.await();
                cartService.addItem(userId, productId, 4);
                return null;
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(5, cartQuantity(userId, productId));
    }

    @Test
    void addWaitsForCompetingStockChangeAndUsesCommittedLockedStock() throws Exception {
        Long userId = insertUser();
        Long productId = insertSaleableProduct(5);
        cartService.addItem(userId, productId, 1);
        CountDownLatch stockUpdated = new CountDownLatch(1);
        CountDownLatch releaseStockTransaction = new CountDownLatch(1);
        CountDownLatch cartMutationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            Future<?> stockChange = executor.submit(() -> {
                transaction.executeWithoutResult(status -> {
                    jdbcTemplate.update("update products set stock_quantity = 2 where id = ?", productId);
                    stockUpdated.countDown();
                    await(releaseStockTransaction);
                });
                return null;
            });
            stockUpdated.await(5, TimeUnit.SECONDS);

            Future<?> cartMutation = executor.submit(() -> {
                cartMutationStarted.countDown();
                cartService.addItem(userId, productId, 10);
                return null;
            });
            cartMutationStarted.await(5, TimeUnit.SECONDS);
            Thread.sleep(200);
            assertFalse(cartMutation.isDone(), "cart mutation must wait for the product row lock");

            releaseStockTransaction.countDown();
            stockChange.get(10, TimeUnit.SECONDS);
            cartMutation.get(10, TimeUnit.SECONDS);
        } finally {
            releaseStockTransaction.countDown();
            executor.shutdownNow();
        }

        assertEquals(2, cartQuantity(userId, productId));
    }

    @Test
    void updateWaitsForCompetingStockChangeAndUsesCommittedLockedStock() throws Exception {
        Long userId = insertUser();
        Long productId = insertSaleableProduct(5);
        cartService.addItem(userId, productId, 1);
        Long cartItemId = cartItemId(userId, productId);
        CountDownLatch stockUpdated = new CountDownLatch(1);
        CountDownLatch releaseStockTransaction = new CountDownLatch(1);
        CountDownLatch cartMutationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        try {
            Future<?> stockChange = executor.submit(() -> {
                transaction.executeWithoutResult(status -> {
                    jdbcTemplate.update("update products set stock_quantity = 2 where id = ?", productId);
                    stockUpdated.countDown();
                    await(releaseStockTransaction);
                });
                return null;
            });
            assertTrue(stockUpdated.await(5, TimeUnit.SECONDS));

            Future<?> cartMutation = executor.submit(() -> {
                cartMutationStarted.countDown();
                cartService.updateItemQuantity(userId, cartItemId, 5);
                return null;
            });
            assertTrue(cartMutationStarted.await(5, TimeUnit.SECONDS));
            Thread.sleep(200);
            assertFalse(cartMutation.isDone(), "cart update must wait for the product row lock");

            releaseStockTransaction.countDown();
            stockChange.get(10, TimeUnit.SECONDS);
            cartMutation.get(10, TimeUnit.SECONDS);
        } finally {
            releaseStockTransaction.countDown();
            executor.shutdownNow();
        }

        int stock = jdbcTemplate.queryForObject(
                "select stock_quantity from products where id = ?", Integer.class, productId);
        int quantity = cartQuantity(userId, productId);
        assertTrue(quantity > 0);
        assertTrue(quantity <= stock);
        assertEquals(2, quantity);
    }

    private Long insertUser() {
        String email = "concurrent-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status,
                                   created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Concurrent Cart User", email, "not-a-real-password-hash");
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long insertSaleableProduct(int stock) {
        String suffix = UUID.randomUUID().toString();
        Long ownerId = insertUser();
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   submitted_at, approved_at, created_at, updated_at)
                values (?, ?, '0900000000', 'Test address', 'APPROVED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, "Concurrency Shop " + suffix);
        Long shopId = jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);
        jdbcTemplate.update("""
                insert into categories (name, description, active, created_at, updated_at)
                values (?, 'Test category', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Concurrency Category " + suffix);
        Long categoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = ?", Long.class, "Concurrency Category " + suffix);
        jdbcTemplate.update("""
                insert into products (shop_id, category_id, name, description, price,
                                      stock_quantity, status, created_at, updated_at)
                values (?, ?, ?, 'Test product', 100000.00, ?, 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, shopId, categoryId, "Concurrency Product " + suffix, stock);
        return jdbcTemplate.queryForObject(
                "select id from products where shop_id = ?", Long.class, shopId);
    }

    private int cartQuantity(Long userId, Long productId) {
        return jdbcTemplate.queryForObject("""
                select ci.quantity
                  from cart_items ci
                  join carts c on c.id = ci.cart_id
                 where c.user_id = ? and ci.product_id = ?
                """, Integer.class, userId, productId);
    }

    private Long cartItemId(Long userId, Long productId) {
        return jdbcTemplate.queryForObject("""
                select ci.id
                  from cart_items ci
                  join carts c on c.id = ci.cart_id
                 where c.user_id = ? and ci.product_id = ?
                """, Long.class, userId, productId);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrency test latch");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrency test interrupted", exception);
        }
    }
}
