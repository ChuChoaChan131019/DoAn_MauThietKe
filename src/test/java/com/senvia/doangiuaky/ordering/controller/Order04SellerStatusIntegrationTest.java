
package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.ordering.api.OrderStatusChangedEvent;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(Order04SellerStatusIntegrationTest.EventTestConfig.class)
class Order04SellerStatusIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    EntityManager entityManager;

    @Autowired
    EventRecorder events;

    private final List<Long> createdOrders = new ArrayList<>();
    private final List<Long> createdShops = new ArrayList<>();
    private final List<Long> createdUsers = new ArrayList<>();

    record Fixture(
            String ownerEmail,
            String buyerEmail,
            Long orderId,
            String orderCode
    ) {}

    @TestConfiguration
    static class EventTestConfig {
        @Bean
        EventRecorder order04EventRecorder() {
            return new EventRecorder();
        }
    }

    static class EventRecorder {
        final List<OrderStatusChangedEvent> received =
                new CopyOnWriteArrayList<>();

        @TransactionalEventListener(
                phase = TransactionPhase.AFTER_COMMIT,
                fallbackExecution = false
        )
        void onStatusChanged(OrderStatusChangedEvent event) {
            received.add(event);
        }
    }

    @BeforeEach
    void clearEvents() {
        events.received.clear();
    }

    @AfterEach
    void cleanup() {
        for (Long orderId : createdOrders) {
            db.update(
                    "DELETE FROM order_status_histories WHERE order_id = ?",
                    orderId
            );
            db.update(
                    "DELETE FROM payments WHERE order_id = ?",
                    orderId
            );
            db.update(
                    "DELETE FROM order_items WHERE order_id = ?",
                    orderId
            );
            db.update(
                    "DELETE FROM orders WHERE id = ?",
                    orderId
            );
        }

        for (Long shopId : createdShops) {
            db.update("DELETE FROM shops WHERE id = ?", shopId);
        }

        for (Long userId : createdUsers) {
            db.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void bankTransferMustBePaidBeforeConfirmation() throws Exception {
        Fixture f = createFixture(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                false
        );

        MvcResult blocked = submit(f, "CONFIRMED");

        assertTrue(blocked.getFlashMap().containsKey("statusError"));
        assertEquals("PENDING", orderStatus(f));
        assertEquals(1, historyCount(f));
        assertTrue(events.received.isEmpty());

        // PAY 05: Xác nhận tiền trước.
        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.orderCode())
                        .with(user(f.ownerEmail()).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("paymentSuccess"));

        assertEquals("PAID", paymentStatus(f));
        assertEquals("PENDING", orderStatus(f));

        // ORDER 04: Sau đó mới xác nhận đơn.
        MvcResult confirmed = submit(f, "CONFIRMED");

        assertTrue(confirmed.getFlashMap().containsKey("statusSuccess"));
        assertEquals("CONFIRMED", orderStatus(f));
        assertEquals(2, historyCount(f));
        assertEquals(1, events.received.size());

        assertEquals(
                "CONFIRMED",
                events.received.get(0).newStatus()
        );
    }

    @Test
    void codCanBeConfirmedWhilePaymentIsPending() throws Exception {
        Fixture f = createFixture(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                false
        );

        MvcResult result = submit(f, "CONFIRMED");

        assertTrue(result.getFlashMap().containsKey("statusSuccess"));
        assertEquals("CONFIRMED", orderStatus(f));
        assertEquals("COD_PENDING", paymentStatus(f));
        assertEquals(2, historyCount(f));
        assertEquals(1, events.received.size());

        String oldStatus = db.queryForObject("""
                SELECT old_status
                FROM order_status_histories
                WHERE order_id = ? AND new_status = 'CONFIRMED'
                """, String.class, f.orderId());

        assertEquals("PENDING", oldStatus);
    }

    @Test
    void inconsistentPaymentTimesCannotAdvanceOrder() throws Exception {
        Fixture bankWithoutPaidAt = createFixture(
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, false);
        Fixture bankWithRefundedAt = createFixture(
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PAID, false);
        Fixture codWithPaidAt = createFixture(
                PaymentMethod.COD, PaymentStatus.COD_PENDING, false);
        Fixture codWithRefundedAt = createFixture(
                PaymentMethod.COD, PaymentStatus.COD_PENDING, false);
        Timestamp unexpectedTime = Timestamp.valueOf("2026-01-01 00:00:00");

        db.update("UPDATE payments SET paid_at = NULL WHERE order_id = ?", bankWithoutPaidAt.orderId());
        db.update("UPDATE payments SET refunded_at = ? WHERE order_id = ?",
                unexpectedTime, bankWithRefundedAt.orderId());
        db.update("UPDATE payments SET paid_at = ? WHERE order_id = ?",
                unexpectedTime, codWithPaidAt.orderId());
        db.update("UPDATE payments SET refunded_at = ? WHERE order_id = ?",
                unexpectedTime, codWithRefundedAt.orderId());

        for (Fixture fixture : new Fixture[]{
                bankWithoutPaidAt, bankWithRefundedAt, codWithPaidAt, codWithRefundedAt}) {
            var paymentBefore = db.queryForMap(
                    "SELECT status, paid_at, refunded_at FROM payments WHERE order_id = ?", fixture.orderId());

            MvcResult response = submit(fixture, "CONFIRMED");

            assertTrue(response.getFlashMap().containsKey("statusError"));
            assertEquals("PENDING", orderStatus(fixture));
            assertEquals(1, historyCount(fixture));
            assertEquals(paymentBefore, db.queryForMap(
                    "SELECT status, paid_at, refunded_at FROM payments WHERE order_id = ?", fixture.orderId()));
        }
        assertTrue(events.received.isEmpty());
    }

    @Test
    void skipAndRepeatedTransitionsAreRejected() throws Exception {
        Fixture f = createFixture(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                false
        );

        // Không được PENDING -> SHIPPING.
        MvcResult skipped = submit(f, "SHIPPING");

        assertTrue(skipped.getFlashMap().containsKey("statusError"));
        assertEquals("PENDING", orderStatus(f));
        assertEquals(1, historyCount(f));

        // PENDING -> CONFIRMED hợp lệ.
        submit(f, "CONFIRMED");

        assertEquals("CONFIRMED", orderStatus(f));
        assertEquals(2, historyCount(f));

        // Không được xác nhận lần hai.
        MvcResult repeated = submit(f, "CONFIRMED");

        assertTrue(repeated.getFlashMap().containsKey("statusError"));
        assertEquals("CONFIRMED", orderStatus(f));
        assertEquals(2, historyCount(f));

        // Không được chuyển lùi.
        MvcResult backwards = submit(f, "PENDING");

        assertTrue(backwards.getFlashMap().containsKey("statusError"));
        assertEquals("CONFIRMED", orderStatus(f));
        assertEquals(2, historyCount(f));

        // Chỉ có một event được xử lý sau commit.
        assertEquals(1, events.received.size());
    }

    @Test
    void foreignSellerAndBuyerCannotChangeOrder() throws Exception {
        Fixture f = createFixture(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                false
        );

        String otherSeller = uniqueEmail("other-seller");
        createUser(otherSeller, "USER");

        mvc.perform(
                post("/merchant/orders/status")
                        .param("id", f.orderCode())
                        .param("status", "CONFIRMED")
                        .with(user(otherSeller).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().isNotFound());

        mvc.perform(
                post("/merchant/orders/status")
                        .param("id", f.orderCode())
                        .param("status", "CONFIRMED")
                        .with(user(f.buyerEmail()).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().isNotFound());

        assertEquals("PENDING", orderStatus(f));
        assertEquals(1, historyCount(f));
        assertTrue(events.received.isEmpty());
    }

    @Test
    void lockedShopCanProcessExistingOrder() throws Exception {
        Fixture f = createFixture(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                true
        );

        MvcResult result = submit(f, "CONFIRMED");

        assertTrue(result.getFlashMap().containsKey("statusSuccess"));
        assertEquals("CONFIRMED", orderStatus(f));
        assertEquals(2, historyCount(f));
    }

    @Test
    void codBecomesPaidOnlyWhenOrderCompletes() throws Exception {
        Fixture f = createFixture(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                false
        );

        submit(f, "CONFIRMED");
        submit(f, "PREPARING");
        submit(f, "SHIPPING");

        assertEquals("COD_PENDING", paymentStatus(f));

        submit(f, "COMPLETED");

        assertEquals("COMPLETED", orderStatus(f));
        assertEquals("PAID", paymentStatus(f));
        assertEquals(5, historyCount(f));

        Timestamp paidAt = db.queryForObject(
                "SELECT paid_at FROM payments WHERE order_id = ?",
                Timestamp.class,
                f.orderId()
        );

        assertNotNull(paidAt);

        MvcResult replay = submit(f, "COMPLETED");

        assertTrue(replay.getFlashMap().containsKey("statusError"));
        assertEquals(5, historyCount(f));
        assertEquals(4, events.received.size());
    }

    @Test
    void missingCsrfAndInvalidStatusAreRejected() throws Exception {
        Fixture f = createFixture(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                false
        );

        mvc.perform(
                post("/merchant/orders/status")
                        .param("id", f.orderCode())
                        .param("status", "CONFIRMED")
                        .with(user(f.ownerEmail()).roles("USER"))
        )
                .andExpect(status().isForbidden());

        mvc.perform(
                post("/merchant/orders/status")
                        .param("id", f.orderCode())
                        .param("status", "INVALID")
                        .with(user(f.ownerEmail()).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().isBadRequest());

        mvc.perform(
                get("/merchant/orders/status")
                        .with(user(f.ownerEmail()).roles("USER"))
        )
                .andExpect(status().isMethodNotAllowed());

        assertEquals("PENDING", orderStatus(f));
        assertEquals(1, historyCount(f));
    }

    @Test
    void rollbackDoesNotSaveHistoryOrDeliverEvent() {
        Fixture f = createFixture(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                false
        );

        transactions.executeWithoutResult(transaction -> {
            try {
                MvcResult result = submit(f, "CONFIRMED");

                assertTrue(result.getFlashMap()
                        .containsKey("statusSuccess"));

                // Đồng bộ các thay đổi JPA để JDBC đọc được trong cùng transaction.
                entityManager.flush();
                assertEquals("CONFIRMED", orderStatus(f));

                // Nhưng event AFTER_COMMIT chưa được giao.
                assertTrue(events.received.isEmpty());

                transaction.setRollbackOnly();

            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });

        assertEquals("PENDING", orderStatus(f));
        assertEquals(1, historyCount(f));
        assertTrue(events.received.isEmpty());
    }

    @Test
    void concurrentRequestsOnlyAdvanceOnce() throws Exception {
        Fixture f = createFixture(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                false
        );

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Boolean> request = () -> {
            ready.countDown();
            start.await();

            MvcResult result = submit(f, "CONFIRMED");

            return result.getFlashMap()
                    .containsKey("statusSuccess");
        };

        try {
            Future<Boolean> first = executor.submit(request);
            Future<Boolean> second = executor.submit(request);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int successes = 0;

            if (first.get(15, TimeUnit.SECONDS)) {
                successes++;
            }

            if (second.get(15, TimeUnit.SECONDS)) {
                successes++;
            }

            assertEquals(1, successes);
            assertEquals("CONFIRMED", orderStatus(f));
            assertEquals(2, historyCount(f));
            assertEquals(1, events.received.size());

        } finally {
            executor.shutdownNow();
        }
    }

    private MvcResult submit(Fixture f, String nextStatus)
            throws Exception {

        return mvc.perform(
                post("/merchant/orders/status")
                        .param("id", f.orderCode())
                        .param("status", nextStatus)
                        .with(user(f.ownerEmail()).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().is3xxRedirection())
                .andReturn();
    }

    private String orderStatus(Fixture f) {
        return db.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                f.orderId()
        );
    }

    private String paymentStatus(Fixture f) {
        return db.queryForObject(
                "SELECT status FROM payments WHERE order_id = ?",
                String.class,
                f.orderId()
        );
    }

    private int historyCount(Fixture f) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM order_status_histories WHERE order_id = ?",
                Integer.class,
                f.orderId()
        );
    }

    private Fixture createFixture(
            PaymentMethod method,
            PaymentStatus paymentStatus,
            boolean locked
    ) {
        String ownerEmail = uniqueEmail("order04-owner");
        String buyerEmail = uniqueEmail("order04-buyer");

        Long ownerId = createUser(ownerEmail, "USER");
        Long buyerId = createUser(buyerEmail, "USER");

        db.update("""
                INSERT INTO shops (
                    owner_id, shop_name, phone, address, status,
                    lock_reason, locked_by, locked_at
                )
                VALUES (?, ?, '0901234567', 'Test address', ?, ?, ?, ?)
                """,
                ownerId,
                "Order04 Shop " + UUID.randomUUID(),
                locked ? "LOCKED" : "APPROVED",
                locked ? "Kiểm thử đơn cũ" : null,
                locked ? ownerId : null,
                locked ? Timestamp.from(Instant.now()) : null
        );

        Long shopId = db.queryForObject(
                "SELECT id FROM shops WHERE owner_id = ?",
                Long.class,
                ownerId
        );
        createdShops.add(shopId);

        String code = "ORD-O04-" +
                UUID.randomUUID().toString()
                        .replace("-", "")
                        .substring(0, 16);

        db.update("""
                INSERT INTO orders (
                    order_code, buyer_id, shop_id, status,
                    receiver_name, receiver_phone,
                    shipping_address, total_amount,
                    created_at, updated_at
                )
                VALUES (
                    ?, ?, ?, 'PENDING',
                    'Order04 Buyer', '0900000000',
                    'Test delivery address', ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                code,
                buyerId,
                shopId,
                new BigDecimal("150000.00")
        );

        Long orderId = db.queryForObject(
                "SELECT id FROM orders WHERE order_code = ?",
                Long.class,
                code
        );
        createdOrders.add(orderId);

        db.update("""
                INSERT INTO payments (
                    order_id, method, amount, status,
                    transaction_reference, paid_at
                )
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                orderId,
                method.name(),
                new BigDecimal("150000.00"),
                paymentStatus.name(),
                method == PaymentMethod.BANK_TRANSFER
                        ? "SENVIA-" + code : null,
                paymentStatus == PaymentStatus.PAID
                        ? Timestamp.from(Instant.now()) : null
        );

        db.update("""
                INSERT INTO order_status_histories (
                    order_id, old_status, new_status,
                    changed_by, note, changed_at
                )
                VALUES (?, NULL, 'PENDING', ?,
                        'Khởi tạo đơn hàng', CURRENT_TIMESTAMP)
                """,
                orderId,
                buyerId
        );

        return new Fixture(
                ownerEmail,
                buyerEmail,
                orderId,
                code
        );
    }

    private Long createUser(String email, String role) {
        db.update("""
                INSERT INTO users (
                    full_name, email, password_hash,
                    role, account_status, created_at, updated_at
                )
                VALUES (
                    'ORDER 04 Tester', ?, 'hash',
                    ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """,
                email,
                role
        );

        Long id = db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                email
        );

        createdUsers.add(id);
        return id;
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
