
package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.ordering.api.OrderStatusChangedEvent;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(Order08CancellationIntegrationTest.EventConfiguration.class)
class Order08CancellationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate db;
    @Autowired TransactionTemplate transactions;
    @Autowired EventRecorder eventRecorder;

    private final List<Fixture> created = new ArrayList<>();

    record Fixture(
            Long orderId,
            Long orderItemId,
            Long productId,
            Long shopId,
            String code,
            Long ownerId,
            Long buyerId,
            Long adminId,
            Long outsiderId,
            String ownerEmail,
            String buyerEmail,
            String adminEmail,
            String outsiderEmail
    ) {}

    @TestConfiguration
    static class EventConfiguration {
        @Bean
        EventRecorder order08EventRecorder() {
            return new EventRecorder();
        }
    }

    static class EventRecorder {
        final List<OrderStatusChangedEvent> events =
                new CopyOnWriteArrayList<>();

        @TransactionalEventListener(
                phase = TransactionPhase.AFTER_COMMIT
        )
        void receive(OrderStatusChangedEvent event) {
            events.add(event);
        }
    }

    @BeforeEach
    void clearEvents() {
        eventRecorder.events.clear();
    }

    @AfterEach
    void cleanup() {
        for (Fixture f : created) {
            db.update("""
                    DELETE FROM stock_restorations
                    WHERE order_item_id IN (
                        SELECT id FROM order_items
                        WHERE order_id = ?
                    )
                    """, f.orderId());

            db.update(
                    "DELETE FROM order_status_histories WHERE order_id = ?",
                    f.orderId()
            );
            db.update(
                    "DELETE FROM payments WHERE order_id = ?",
                    f.orderId()
            );
            db.update(
                    "DELETE FROM order_items WHERE order_id = ?",
                    f.orderId()
            );
            db.update(
                    "DELETE FROM orders WHERE id = ?",
                    f.orderId()
            );
            db.update(
                    "DELETE FROM products WHERE id = ?",
                    f.productId()
            );
            db.update(
                    "DELETE FROM shops WHERE id = ?",
                    f.shopId()
            );

            for (Long id : List.of(
                    f.ownerId(),
                    f.buyerId(),
                    f.adminId(),
                    f.outsiderId()
            )) {
                db.update("DELETE FROM users WHERE id = ?", id);
            }
        }

        created.clear();
        eventRecorder.events.clear();
    }

    @Test
    void codCancellationRestoresOnceAndIsNotRepeatable()
            throws Exception {
        Fixture f = fixture(
                "PENDING", "COD", "COD_PENDING", false
        );

        MvcResult result = sellerCancel(f, "  Khách đổi ý  ");

        assertTrue(result.getFlashMap()
                .containsKey("cancelSuccess"));

        assertEquals("CANCELLED", orderStatus(f));
        assertEquals("CANCELLED", paymentStatus(f));
        assertEquals(5, stock(f));
        assertEquals(1, restorationCount(f));
        assertEquals(2, historyCount(f));
        assertEquals(1, eventRecorder.events.size());

        assertEquals(
                "CANCELLED",
                eventRecorder.events.get(0).newStatus()
        );

        assertEquals(
                "Hủy đơn. Lý do: Khách đổi ý",
                db.queryForObject("""
                        SELECT note FROM order_status_histories
                        WHERE order_id = ?
                          AND new_status = 'CANCELLED'
                        """, String.class, f.orderId())
        );

        // Gửi lại không được hoàn kho thêm.
        MvcResult repeat = sellerCancel(f, "Hủy lại");

        assertTrue(repeat.getFlashMap()
                .containsKey("cancelError"));

        assertEquals(5, stock(f));
        assertEquals(1, restorationCount(f));
        assertEquals(2, historyCount(f));
        assertEquals(1, eventRecorder.events.size());
    }

    @Test
    void paidBankTransferIsMarkedRefunded()
            throws Exception {
        Fixture f = fixture(
                "CONFIRMED", "BANK_TRANSFER", "PAID", false
        );

        sellerCancel(f, "Hoàn tiền mô phỏng");

        assertEquals("CANCELLED", orderStatus(f));
        assertEquals("REFUNDED", paymentStatus(f));

        assertNotNull(db.queryForObject(
                "SELECT refunded_at FROM payments WHERE order_id = ?",
                Timestamp.class,
                f.orderId()
        ));

        assertNotNull(db.queryForObject(
                "SELECT paid_at FROM payments WHERE order_id = ?",
                Timestamp.class,
                f.orderId()
        ));

        assertEquals(5, stock(f));
    }

    @Test
    void buyerCanCancelAndAdminCanCancelButOutsiderCannot()
            throws Exception {

        Fixture buyerOrder = fixture(
                "PREPARING", "COD", "COD_PENDING", false
        );

        mvc.perform(
                post("/account/orders/cancel")
                        .param("id", buyerOrder.code())
                        .param("reason", "Đổi nhu cầu")
                        .with(user(buyerOrder.buyerEmail())
                                .roles("USER"))
                        .with(csrf())
        ).andExpect(status().is3xxRedirection());

        assertEquals("CANCELLED", orderStatus(buyerOrder));

        Fixture adminOrder = fixture(
                "PENDING", "COD", "COD_PENDING", false
        );

        mvc.perform(
                post("/admin/orders/cancel")
                        .param("id", adminOrder.code())
                        .param("reason", "Đơn không hợp lệ")
                        .with(user(adminOrder.adminEmail())
                                .roles("ADMIN"))
                        .with(csrf())
        ).andExpect(status().is3xxRedirection());

        assertEquals("CANCELLED", orderStatus(adminOrder));

        Fixture untouched = fixture(
                "PENDING", "COD", "COD_PENDING", false
        );

        mvc.perform(
                post("/merchant/orders/cancel")
                        .param("id", untouched.code())
                        .param("reason", "Không có quyền")
                        .with(user(untouched.outsiderEmail())
                                .roles("USER"))
                        .with(csrf())
        ).andExpect(status().isNotFound());

        assertEquals("PENDING", orderStatus(untouched));
        assertEquals(3, stock(untouched));
    }

    @Test
    void lockedShopCanCancelOldOrder()
            throws Exception {
        Fixture f = fixture(
                "PREPARING", "COD", "COD_PENDING", true
        );

        sellerCancel(f, "Đơn cũ không thể giao");

        assertEquals("CANCELLED", orderStatus(f));
        assertEquals(5, stock(f));
    }

    @Test
    void shippingAndInvalidReasonCannotCancel()
            throws Exception {
        Fixture f = fixture(
                "SHIPPING", "COD", "COD_PENDING", false
        );

        assertTrue(sellerCancel(f, "Không nhận")
                .getFlashMap().containsKey("cancelError"));

        assertEquals("SHIPPING", orderStatus(f));
        assertEquals(3, stock(f));
        assertEquals(0, restorationCount(f));

        Fixture pending = fixture(
                "PENDING", "COD", "COD_PENDING", false
        );

        assertTrue(sellerCancel(pending, "   ")
                .getFlashMap().containsKey("cancelError"));

        assertTrue(sellerCancel(pending, "x".repeat(501))
                .getFlashMap().containsKey("cancelError"));

        assertEquals("PENDING", orderStatus(pending));
        assertEquals(3, stock(pending));
        assertEquals(0, restorationCount(pending));
    }

    @Test
    void rollbackRestoresOrderPaymentStockAndSuppressesEvent() {
        Fixture f = fixture(
                "PENDING", "COD", "COD_PENDING", false
        );

        assertThrows(IllegalStateException.class, () ->
                transactions.executeWithoutResult(tx -> {
                    try {
                        MvcResult result = sellerCancel(
                                f, "Rollback thử nghiệm"
                        );

                        assertTrue(result.getFlashMap()
                                .containsKey("cancelSuccess"));

                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }

                    throw new IllegalStateException(
                            "Buộc rollback toàn bộ transaction"
                    );
                })
        );

        assertEquals("PENDING", orderStatus(f));
        assertEquals("COD_PENDING", paymentStatus(f));
        assertEquals(3, stock(f));
        assertEquals(0, restorationCount(f));
        assertEquals(1, historyCount(f));
        assertTrue(eventRecorder.events.isEmpty());
    }

    @Test
    void concurrentRequestsCancelOnlyOnce()
            throws Exception {
        Fixture f = fixture(
                "PENDING", "COD", "COD_PENDING", false
        );

        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            Callable<Boolean> attempt = () -> {
                ready.countDown();
                start.await();

                return sellerCancel(f, "Hủy đồng thời")
                        .getFlashMap()
                        .containsKey("cancelSuccess");
            };

            var a = executor.submit(attempt);
            var b = executor.submit(attempt);

            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();

            int successes =
                    (a.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (b.get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertEquals(1, successes);
        }

        assertEquals("CANCELLED", orderStatus(f));
        assertEquals(5, stock(f));
        assertEquals(1, restorationCount(f));
        assertEquals(2, historyCount(f));
        assertEquals(1, eventRecorder.events.size());
    }

    private MvcResult sellerCancel(
            Fixture f,
            String reason
    ) throws Exception {
        return mvc.perform(
                post("/merchant/orders/cancel")
                        .param("id", f.code())
                        .param("reason", reason)
                        .with(user(f.ownerEmail()).roles("USER"))
                        .with(csrf())
        ).andExpect(status().is3xxRedirection())
         .andReturn();
    }

    private Fixture fixture(
            String orderStatus,
            String method,
            String paymentStatus,
            boolean lockedShop
    ) {
        // Nếu tạo fixture lỗi ở bất kỳ bước nào, transaction rollback toàn bộ dữ liệu test.
        Fixture fixture = transactions.execute(transaction ->
                createFixture(orderStatus, method, paymentStatus, lockedShop));
        created.add(fixture);
        return fixture;
    }

    private Fixture createFixture(
            String orderStatus,
            String method,
            String paymentStatus,
            boolean lockedShop
    ) {
        String key = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16);

        String ownerEmail =
                "order08-owner-" + key + "@example.com";
        String buyerEmail =
                "order08-buyer-" + key + "@example.com";
        String adminEmail =
                "order08-admin-" + key + "@example.com";
        String outsiderEmail =
                "order08-other-" + key + "@example.com";

        Long ownerId = userId(ownerEmail, "USER");
        Long buyerId = userId(buyerEmail, "USER");
        Long adminId = userId(adminEmail, "ADMIN");
        Long outsiderId = userId(outsiderEmail, "USER");

        db.update("""
                INSERT INTO shops(
                    owner_id, shop_name, phone, address, status,
                    lock_reason, locked_by, locked_at
                )
                VALUES (
                    ?, ?, '0901234567', 'Test address', ?, ?, ?, ?
                )
                """,
                ownerId,
                "ORDER08 " + key,
                lockedShop ? "LOCKED" : "APPROVED",
                lockedShop ? "Shop bị khóa" : null,
                lockedShop ? adminId : null,
                lockedShop ? Timestamp.from(Instant.now()) : null
        );

        Long shopId = db.queryForObject(
                "SELECT id FROM shops WHERE owner_id = ?",
                Long.class, ownerId
        );

        Long categoryId = db.queryForObject(
                "SELECT id FROM categories ORDER BY id LIMIT 1",
                Long.class
        );

        db.update("""
                INSERT INTO products(
                    shop_id, category_id, name, description,
                    price, stock_quantity, status
                )
                VALUES (
                    ?, ?, ?, 'ORDER08 integration',
                    100000.00, 3, 'ACTIVE'
                )
                """,
                shopId, categoryId, "ORDER08 Item " + key
        );

        Long productId = db.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                "ORDER08 Item " + key
        );

        String code = "ORD-O08-" + key;

        db.update("""
                INSERT INTO orders(
                    order_code, buyer_id, shop_id, status,
                    receiver_name, receiver_phone,
                    shipping_address, total_amount
                )
                VALUES (
                    ?, ?, ?, ?, 'Buyer Test', '0900000000',
                    'Address Test', 200000.00
                )
                """,
                code, buyerId, shopId, orderStatus
        );

        Long orderId = db.queryForObject(
                "SELECT id FROM orders WHERE order_code = ?",
                Long.class, code
        );

        db.update("""
                INSERT INTO order_items(
                    order_id, product_id,
                    product_name_snapshot,
                    unit_price, quantity, subtotal
                )
                VALUES (
                    ?, ?, 'ORDER08 Item',
                    100000.00, 2, 200000.00
                )
                """,
                orderId, productId
        );

        Long itemId = db.queryForObject(
                "SELECT id FROM order_items WHERE order_id = ?",
                Long.class, orderId
        );

        db.update("""
                INSERT INTO payments(
                    order_id, method, amount, status,
                    transaction_reference, paid_at
                )
                VALUES (?, ?, 200000.00, ?, ?, ?)
                """,
                orderId,
                method,
                paymentStatus,
                method.equals("BANK_TRANSFER")
                        ? "SENVIA-" + key : null,
                paymentStatus.equals("PAID")
                        ? Timestamp.from(Instant.now()) : null
        );

        db.update("""
                INSERT INTO order_status_histories(
                    order_id, old_status, new_status,
                    changed_by, note
                )
                VALUES (?, NULL, 'PENDING', ?, 'Khởi tạo fixture')
                """,
                orderId, buyerId
        );

        List<String> statusFlow = List.of(
                "PENDING", "CONFIRMED", "PREPARING", "SHIPPING", "COMPLETED");
        int targetIndex = statusFlow.indexOf(orderStatus);
        if (targetIndex < 0) {
            throw new IllegalArgumentException("Trạng thái fixture không hợp lệ: " + orderStatus);
        }
        for (int i = 1; i <= targetIndex; i++) {
            db.update("""
                    INSERT INTO order_status_histories(
                        order_id, old_status, new_status,
                        changed_by, note
                    )
                    VALUES (?, ?, ?, ?, 'Chuyển trạng thái fixture')
                    """,
                    orderId, statusFlow.get(i - 1), statusFlow.get(i), ownerId
            );
        }

        Fixture fixture = new Fixture(
                orderId, itemId, productId, shopId, code,
                ownerId, buyerId, adminId, outsiderId,
                ownerEmail, buyerEmail, adminEmail, outsiderEmail
        );

        return fixture;
    }

    private Long userId(String email, String role) {
        db.update("""
                INSERT INTO users(
                    full_name, email, password_hash,
                    role, account_status
                )
                VALUES (
                    'ORDER08 user', ?, 'hash', ?, 'ACTIVE'
                )
                """, email, role);

        return db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class, email
        );
    }

    private String orderStatus(Fixture f) {
        return db.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class, f.orderId()
        );
    }

    private String paymentStatus(Fixture f) {
        return db.queryForObject(
                "SELECT status FROM payments WHERE order_id = ?",
                String.class, f.orderId()
        );
    }

    private int stock(Fixture f) {
        return db.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?",
                Integer.class, f.productId()
        );
    }

    private int restorationCount(Fixture f) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM stock_restorations WHERE order_item_id = ?",
                Integer.class, f.orderItemId()
        );
    }

    private int historyCount(Fixture f) {
        return db.queryForObject(
                "SELECT COUNT(*) FROM order_status_histories WHERE order_id = ?",
                Integer.class, f.orderId()
        );
    }
}
