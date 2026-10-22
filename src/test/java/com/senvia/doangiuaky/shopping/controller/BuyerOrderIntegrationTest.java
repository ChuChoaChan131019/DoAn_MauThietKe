package com.senvia.doangiuaky.shopping.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BuyerOrderIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private EntityManager entityManager;

    record Fixture(
            Long ownerId,
            Long buyerId,
            Long foreignBuyerId,
            Long adminId,
            Long lockedUserId,
            Long shopId,
            Long productId,
            String ownerEmail,
            String buyerEmail,
            String foreignBuyerEmail,
            String adminEmail,
            String lockedUserEmail
    ) {
    }

    @Test
    void listFiltersByCurrentBuyerAndStatus() throws Exception {
        Fixture f = fixture();
        String buyerConfirmed = createOrder(f, f.buyerId(), "CONFIRMED", "COD", "COD_PENDING", "confirmed");
        String buyerPending = createOrder(f, f.buyerId(), "PENDING", "COD", "COD_PENDING", "pending");
        String foreignConfirmed = createOrder(f, f.foreignBuyerId(), "CONFIRMED", "COD", "COD_PENDING", "foreign");

        mvc.perform(get("/account/orders")
                        .param("status", "CONFIRMED")
                        .with(user(f.buyerEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("shopping/orders"))
                .andExpect(content().string(containsString(buyerConfirmed)))
                .andExpect(content().string(not(containsString(buyerPending))))
                .andExpect(content().string(not(containsString(foreignConfirmed))));
    }

    @Test
    void listUsesDatabasePaginationNewestFirst() throws Exception {
        Fixture f = fixture();
        String oldest = null;
        String newest = null;
        for (int i = 0; i < 11; i++) {
            String code = createOrder(f, f.buyerId(), "PENDING", "COD", "COD_PENDING", "page" + i);
            if (i == 0) {
                oldest = code;
            }
            if (i == 10) {
                newest = code;
            }
        }

        mvc.perform(get("/account/orders")
                        .param("page", "2")
                        .with(user(f.buyerEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(oldest)))
                .andExpect(content().string(not(containsString(newest))));
    }

    @Test
    void invalidPageAndStatusReturnBadRequest() throws Exception {
        Fixture f = fixture();
        createOrder(f, f.buyerId(), "PENDING", "COD", "COD_PENDING", "invalid-page");

        mvc.perform(get("/account/orders")
                        .param("page", "2")
                        .with(user(f.buyerEmail()).roles("USER")))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/account/orders")
                        .param("status", "UNKNOWN")
                        .with(user(f.buyerEmail()).roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void detailRendersSnapshotPaymentHistoryAndAllowedCancelForm() throws Exception {
        Fixture f = fixture();
        String code = createOrder(f, f.buyerId(), "PENDING", "BANK_TRANSFER", "PAID", "detail");

        mvc.perform(get("/account/orders/detail")
                        .param("id", code)
                        .with(user(f.buyerEmail()).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("shopping/order-detail"))
                .andExpect(content().string(containsString(code)))
                .andExpect(content().string(containsString("Buyer Snapshot")))
                .andExpect(content().string(containsString("Gọi trước khi giao")))
                .andExpect(content().string(containsString("Snapshot product")))
                .andExpect(content().string(containsString("SENVIA-")))
                .andExpect(content().string(containsString("Khởi tạo đơn hàng")))
                .andExpect(content().string(containsString("/account/orders/cancel")))
                .andExpect(content().string(containsString("maxlength=\"500\"")))
                .andExpect(content().string(containsString("_csrf")));
    }

    @Test
    void foreignAndMissingOrderDetailsReturnNotFoundWithoutLeakingCode() throws Exception {
        Fixture f = fixture();
        String foreignCode = createOrder(f, f.foreignBuyerId(), "PENDING", "COD", "COD_PENDING", "foreign-detail");

        mvc.perform(get("/account/orders/detail")
                        .param("id", foreignCode)
                        .with(user(f.buyerEmail()).roles("USER")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(foreignCode))));

        mvc.perform(get("/account/orders/detail")
                        .param("id", "DOES-NOT-EXIST")
                        .with(user(f.buyerEmail()).roles("USER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelFormOnlyAppearsForAllowedStatuses() throws Exception {
        Fixture f = fixture();
        String shipping = createOrder(f, f.buyerId(), "SHIPPING", "COD", "COD_PENDING", "shipping");
        String completed = createOrder(f, f.buyerId(), "COMPLETED", "COD", "PAID", "completed");
        String cancelled = createOrder(f, f.buyerId(), "CANCELLED", "COD", "CANCELLED", "cancelled");

        for (String code : List.of(shipping, completed, cancelled)) {
            mvc.perform(get("/account/orders/detail")
                            .param("id", code)
                            .with(user(f.buyerEmail()).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("/account/orders/cancel"))));
        }
    }

    @Test
    void validCancelUsesPublicCommandAndUpdatesOrderDataOnce() throws Exception {
        Fixture f = fixture();
        String code = createOrder(f, f.buyerId(), "PENDING", "COD", "COD_PENDING", "cancel");

        mvc.perform(post("/account/orders/cancel")
                        .param("id", code)
                        .param("reason", "Đổi nhu cầu mua hàng")
                        .with(user(f.buyerEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/orders/detail?id=" + code))
                .andExpect(flash().attribute("cancelSuccess", "Đã hủy đơn hàng."));

        entityManager.flush();
        assertEquals("CANCELLED", orderStatus(code));
        assertEquals("CANCELLED", paymentStatus(code));
        assertEquals(5, stock(f.productId()));
        assertEquals(2, historyCount(code));
    }

    @Test
    void invalidReasonAndForbiddenStateReturnFlashErrorsWithoutMutation() throws Exception {
        Fixture f = fixture();
        String pending = createOrder(f, f.buyerId(), "PENDING", "COD", "COD_PENDING", "reason");
        String shipping = createOrder(f, f.buyerId(), "SHIPPING", "COD", "COD_PENDING", "state");

        mvc.perform(post("/account/orders/cancel")
                        .param("id", pending)
                        .param("reason", "   ")
                        .with(user(f.buyerEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/orders/detail?id=" + pending))
                .andExpect(flash().attribute("cancelError", "Lý do hủy phải có từ 1 đến 500 ký tự."));

        mvc.perform(post("/account/orders/cancel")
                        .param("id", shipping)
                        .param("reason", "Không nhận hàng")
                        .with(user(f.buyerEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/orders/detail?id=" + shipping))
                .andExpect(flash().attribute("cancelError", "Đơn ở trạng thái SHIPPING không thể hủy."));

        assertEquals("PENDING", orderStatus(pending));
        assertEquals("SHIPPING", orderStatus(shipping));
    }

    @Test
    void guestAdminLockedAndMissingCsrfCannotUseBuyerOrderFlow() throws Exception {
        Fixture f = fixture();
        String code = createOrder(f, f.buyerId(), "PENDING", "COD", "COD_PENDING", "security");

        mvc.perform(get("/account/orders"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mvc.perform(get("/account/orders")
                        .with(user(f.adminEmail()).roles("ADMIN")))
                .andExpect(status().isForbidden());

        mvc.perform(get("/account/orders")
                        .with(user(f.lockedUserEmail()).roles("USER")))
                .andExpect(status().isForbidden());

        mvc.perform(post("/account/orders/cancel")
                        .param("id", code)
                        .param("reason", "Không có CSRF")
                        .with(user(f.buyerEmail()).roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void foreignBuyerCannotCancelOrder() throws Exception {
        Fixture f = fixture();
        String code = createOrder(f, f.buyerId(), "PENDING", "COD", "COD_PENDING", "foreign-cancel");

        mvc.perform(post("/account/orders/cancel")
                        .param("id", code)
                        .param("reason", "Không có quyền")
                        .with(user(f.foreignBuyerEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertEquals("PENDING", orderStatus(code));
    }

    private Fixture fixture() {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String ownerEmail = "buyer-order-owner-" + key + "@example.com";
        String buyerEmail = "buyer-order-buyer-" + key + "@example.com";
        String foreignEmail = "buyer-order-foreign-" + key + "@example.com";
        String adminEmail = "buyer-order-admin-" + key + "@example.com";
        String lockedEmail = "buyer-order-locked-" + key + "@example.com";

        Long ownerId = userId(ownerEmail, "USER", "ACTIVE");
        Long buyerId = userId(buyerEmail, "USER", "ACTIVE");
        Long foreignBuyerId = userId(foreignEmail, "USER", "ACTIVE");
        Long adminId = userId(adminEmail, "ADMIN", "ACTIVE");
        Long lockedUserId = userId(lockedEmail, "USER", "LOCKED");

        db.update("""
                INSERT INTO shops (
                    owner_id, shop_name, phone, address, status
                ) VALUES (?, ?, '0900000000', 'Test address', 'APPROVED')
                """, ownerId, "Buyer Order Shop " + key);
        Long shopId = db.queryForObject(
                "SELECT id FROM shops WHERE owner_id = ?", Long.class, ownerId);

        Long categoryId = db.queryForObject(
                "SELECT id FROM categories ORDER BY id LIMIT 1", Long.class);
        db.update("""
                INSERT INTO products (
                    shop_id, category_id, name, description,
                    price, stock_quantity, status
                ) VALUES (?, ?, ?, 'Buyer order integration', 100000.00, 3, 'ACTIVE')
                """, shopId, categoryId, "Buyer Order Product " + key);
        Long productId = db.queryForObject(
                "SELECT id FROM products WHERE name = ?", Long.class,
                "Buyer Order Product " + key);

        return new Fixture(
                ownerId, buyerId, foreignBuyerId, adminId, lockedUserId,
                shopId, productId, ownerEmail, buyerEmail, foreignEmail,
                adminEmail, lockedEmail
        );
    }

    private String createOrder(
            Fixture fixture,
            Long buyerId,
            String orderStatus,
            String paymentMethod,
            String paymentStatus,
            String suffix
    ) {
        String code = "BO60-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        db.update("""
                INSERT INTO orders (
                    order_code, buyer_id, shop_id, status,
                    receiver_name, receiver_phone, shipping_address, note,
                    total_amount, created_at, updated_at
                ) VALUES (?, ?, ?, ?, 'Buyer Snapshot', '0900000000',
                    'Test address', 'Gọi trước khi giao', 200000.00,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, code, buyerId, fixture.shopId(), orderStatus);
        Long orderId = db.queryForObject(
                "SELECT id FROM orders WHERE order_code = ?", Long.class, code);

        db.update("""
                INSERT INTO order_items (
                    order_id, product_id, product_name_snapshot,
                    unit_price, quantity, subtotal
                ) VALUES (?, ?, 'Snapshot product', 100000.00, 2, 200000.00)
                """, orderId, fixture.productId());

        db.update("""
                INSERT INTO payments (
                    order_id, method, amount, status,
                    transaction_reference, paid_at
                ) VALUES (?, ?, 200000.00, ?, ?, ?)
                """, orderId, paymentMethod, paymentStatus,
                paymentMethod.equals("BANK_TRANSFER") ? "SENVIA-" + code : null,
                paymentStatus.equals("PAID") ? Timestamp.from(Instant.now()) : null);

        db.update("""
                INSERT INTO order_status_histories (
                    order_id, old_status, new_status, changed_by, note
                ) VALUES (?, NULL, 'PENDING', ?, 'Khởi tạo đơn hàng')
                """, orderId, buyerId);

        List<String> flow = List.of(
                "PENDING", "CONFIRMED", "PREPARING", "SHIPPING", "COMPLETED", "CANCELLED");
        int target = flow.indexOf(orderStatus);
        if (target < 0) {
            throw new IllegalArgumentException("Unsupported order status: " + orderStatus);
        }
        for (int i = 1; i <= target; i++) {
            db.update("""
                    INSERT INTO order_status_histories (
                        order_id, old_status, new_status, changed_by, note
                    ) VALUES (?, ?, ?, ?, 'Fixture status change')
                    """, orderId, flow.get(i - 1), flow.get(i), fixture.ownerId());
        }

        return code;
    }

    private Long userId(String email, String role, String accountStatus) {
        String accountStatusForInsert = "LOCKED".equals(accountStatus) ? "ACTIVE" : accountStatus;
        db.update("""
                INSERT INTO users (
                    full_name, email, password_hash,
                    role, account_status, created_at, updated_at
                ) VALUES ('Buyer Order Test User', ?, 'hash', ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, email, role, accountStatusForInsert);
        Long id = db.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
        if ("LOCKED".equals(accountStatus)) {
            db.update("""
                    UPDATE users
                    SET account_status = 'LOCKED',
                        lock_reason = 'Synthetic integration-test lock',
                        locked_by = ?, locked_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, id, id);
        }
        return id;
    }

    private String orderStatus(String orderCode) {
        return db.queryForObject(
                "SELECT status FROM orders WHERE order_code = ?", String.class, orderCode);
    }

    private String paymentStatus(String orderCode) {
        return db.queryForObject("""
                SELECT p.status
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                WHERE o.order_code = ?
                """, String.class, orderCode);
    }

    private int historyCount(String orderCode) {
        return db.queryForObject("""
                SELECT COUNT(*)
                FROM order_status_histories h
                JOIN orders o ON o.id = h.order_id
                WHERE o.order_code = ?
                """, Integer.class, orderCode);
    }

    private int stock(Long productId) {
        return db.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, productId);
    }
}
