package com.senvia.doangiuaky.shopping.service;

import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;
import com.senvia.doangiuaky.shopping.entity.Cart;
import com.senvia.doangiuaky.shopping.entity.CartItem;
import com.senvia.doangiuaky.shopping.repository.CartItemRepository;
import com.senvia.doangiuaky.shopping.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CheckoutCartServiceIntegrationTest {

    @Autowired
    private CheckoutCartApi checkoutCartApi;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void bothCallsRequireAnExistingTransaction() {
        assertThrows(IllegalTransactionStateException.class,
                checkoutCartApi::getCheckoutSnapshot);
        assertThrows(IllegalTransactionStateException.class,
                () -> checkoutCartApi.removePurchasedItems(List.of()));
    }

    @Test
    void snapshotRequiresAuthenticatedPrincipalInsideCheckoutTransaction() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> inTransaction(checkoutCartApi::getCheckoutSnapshot));
    }

    @Test
    void snapshotOfMissingOrEmptyCartIsEmpty() {
        Long buyerId = insertUser();
        authenticate(buyerId);
        inTransaction(() -> assertTrue(checkoutCartApi.getCheckoutSnapshot().isEmpty()));
        cartRepository.saveAndFlush(new Cart(buyerId));
        inTransaction(() -> assertTrue(checkoutCartApi.getCheckoutSnapshot().isEmpty()));
    }

    @Test
    void removesOnlySelectedItemsAndRollsBackWithCheckoutTransaction() {
        Fixture fixture = createFixture();
        authenticate(fixture.buyerId());
        List<CheckoutCartItem> snapshot = transaction().execute(status ->
                checkoutCartApi.getCheckoutSnapshot());
        assertEquals(List.of(
                new CheckoutCartItem(fixture.firstItemId(), fixture.firstProductId(), 2),
                new CheckoutCartItem(fixture.secondItemId(), fixture.secondProductId(), 3)), snapshot);

        transaction().executeWithoutResult(status -> {
            checkoutCartApi.removePurchasedItems(List.of(snapshot.getFirst()));
            assertFalse(cartItemRepository.existsById(fixture.firstItemId()));
            status.setRollbackOnly();
        });
        assertTrue(cartItemRepository.existsById(fixture.firstItemId()));
        assertTrue(cartItemRepository.existsById(fixture.secondItemId()));

        inTransaction(() -> checkoutCartApi.removePurchasedItems(List.of(snapshot.getFirst())));
        assertFalse(cartItemRepository.existsById(fixture.firstItemId()));
        assertTrue(cartItemRepository.existsById(fixture.secondItemId()));
    }

    @Test
    void rejectsInvalidDuplicateForeignAndChangedItemsWithoutPartialRemoval() {
        Fixture fixture = createFixture();
        authenticate(fixture.buyerId());
        CheckoutCartItem first = new CheckoutCartItem(fixture.firstItemId(), fixture.firstProductId(), 2);
        List<List<CheckoutCartItem>> invalidInputs = Arrays.asList(
                null, List.of(), Arrays.asList((CheckoutCartItem) null),
                List.of(new CheckoutCartItem(null, fixture.firstProductId(), 2)),
                List.of(new CheckoutCartItem(fixture.firstItemId(), null, 2)),
                List.of(new CheckoutCartItem(fixture.firstItemId(), fixture.firstProductId(), 0)),
                List.of(first, first));
        for (List<CheckoutCartItem> invalid : invalidInputs) {
            assertThrows(CartOperationException.class,
                    () -> inTransaction(() -> checkoutCartApi.removePurchasedItems(invalid)));
        }

        Long otherBuyerId = insertUser();
        Cart otherCart = cartRepository.saveAndFlush(new Cart(otherBuyerId));
        CartItem foreignItem = cartItemRepository.saveAndFlush(
                new CartItem(otherCart.getId(), fixture.firstProductId(), 1));
        assertThrows(CartOperationException.class, () -> inTransaction(() -> checkoutCartApi.removePurchasedItems(
                List.of(first, new CheckoutCartItem(foreignItem.getId(), fixture.firstProductId(), 1)))));
        assertThrows(CartOperationException.class, () -> inTransaction(() -> checkoutCartApi.removePurchasedItems(
                List.of(new CheckoutCartItem(fixture.firstItemId(), fixture.secondProductId(), 2)))));
        assertThrows(CartOperationException.class, () -> inTransaction(() -> checkoutCartApi.removePurchasedItems(
                List.of(new CheckoutCartItem(fixture.firstItemId(), fixture.firstProductId(), 99)))));

        assertTrue(cartItemRepository.existsById(fixture.firstItemId()));
        assertTrue(cartItemRepository.existsById(fixture.secondItemId()));
        assertTrue(cartItemRepository.existsById(foreignItem.getId()));
    }

    @Test
    void failedRemovalRollsBackEarlierChangesAndReplayDoesNotDeleteReplacement() {
        Fixture fixture = createFixture();
        authenticate(fixture.buyerId());
        CheckoutCartItem purchased = new CheckoutCartItem(fixture.firstItemId(), fixture.firstProductId(), 2);
        assertThrows(CartOperationException.class, () -> inTransaction(() -> {
            CartItem changed = cartItemRepository.findById(fixture.firstItemId()).orElseThrow();
            changed.setQuantity(4);
            cartItemRepository.saveAndFlush(changed);
            checkoutCartApi.removePurchasedItems(List.of(purchased));
        }));
        assertEquals(2, cartItemRepository.findById(fixture.firstItemId()).orElseThrow().getQuantity());

        inTransaction(() -> checkoutCartApi.removePurchasedItems(List.of(purchased)));
        Cart cart = cartRepository.findByUserId(fixture.buyerId()).orElseThrow();
        CartItem replacement = cartItemRepository.saveAndFlush(
                new CartItem(cart.getId(), fixture.firstProductId(), 2));
        assertThrows(CartOperationException.class, () -> inTransaction(() ->
                checkoutCartApi.removePurchasedItems(List.of(purchased))));
        assertTrue(cartItemRepository.existsById(replacement.getId()));
        assertTrue(cartItemRepository.existsById(fixture.secondItemId()));
    }

    @Test
    void snapshotKeepsCartLockUntilCheckoutTransactionEnds() throws Exception {
        Fixture fixture = createFixture();
        CountDownLatch snapshotTaken = new CountDownLatch(1);
        CountDownLatch finishCheckout = new CountDownLatch(1);
        CountDownLatch competingLockAttempt = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> checkout = executor.submit(() -> transaction().executeWithoutResult(status -> {
                authenticate(fixture.buyerId());
                try {
                    checkoutCartApi.getCheckoutSnapshot();
                    snapshotTaken.countDown();
                    await(finishCheckout);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }));
            assertTrue(snapshotTaken.await(5, TimeUnit.SECONDS));
            Future<?> competingEdit = executor.submit(() -> transaction().executeWithoutResult(status -> {
                competingLockAttempt.countDown();
                cartRepository.findByUserIdForUpdate(fixture.buyerId()).orElseThrow();
            }));
            assertTrue(competingLockAttempt.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> competingEdit.get(200, TimeUnit.MILLISECONDS));
            finishCheckout.countDown();
            checkout.get(5, TimeUnit.SECONDS);
            competingEdit.get(5, TimeUnit.SECONDS);
        } finally {
            finishCheckout.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to finish checkout");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private void inTransaction(Runnable action) {
        transaction().executeWithoutResult(status -> action.run());
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private void authenticate(Long buyerId) {
        String email = jdbcTemplate.queryForObject(
                "select email from users where id = ?", String.class, buyerId);
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(email, null, "ROLE_USER"));
    }

    private Fixture createFixture() {
        Long buyerId = insertUser();
        Long ownerId = insertUser();
        String suffix = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   submitted_at, approved_at, created_at, updated_at)
                values (?, ?, '0900000000', 'Test address', 'APPROVED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, "checkout-shop-" + suffix);
        Long shopId = jdbcTemplate.queryForObject("select id from shops where shop_name = ?", Long.class,
                "checkout-shop-" + suffix);
        jdbcTemplate.update("""
                insert into categories (name, active, created_at, updated_at)
                values (?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "checkout-category-" + suffix);
        Long categoryId = jdbcTemplate.queryForObject("select id from categories where name = ?", Long.class,
                "checkout-category-" + suffix);
        Long firstProductId = insertProduct(shopId, categoryId, "checkout-first-" + suffix);
        Long secondProductId = insertProduct(shopId, categoryId, "checkout-second-" + suffix);
        Cart cart = cartRepository.saveAndFlush(new Cart(buyerId));
        CartItem first = cartItemRepository.saveAndFlush(new CartItem(cart.getId(), firstProductId, 2));
        CartItem second = cartItemRepository.saveAndFlush(new CartItem(cart.getId(), secondProductId, 3));
        return new Fixture(buyerId, first.getId(), second.getId(), firstProductId, secondProductId);
    }

    private Long insertUser() {
        String email = "checkout-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values ('Checkout Test User', ?, 'not-a-real-password-hash', 'USER', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, email);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long insertProduct(Long shopId, Long categoryId, String name) {
        jdbcTemplate.update("""
                insert into products (shop_id, category_id, name, description, price, stock_quantity, status,
                                      created_at, updated_at)
                values (?, ?, ?, 'Checkout integration test product', 100.00, 10, 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, shopId, categoryId, name);
        return jdbcTemplate.queryForObject("select id from products where name = ?", Long.class, name);
    }

    private record Fixture(Long buyerId, Long firstItemId, Long secondItemId,
                           Long firstProductId, Long secondProductId) {
    }
}
