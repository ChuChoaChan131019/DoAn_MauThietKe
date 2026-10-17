
package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class Pay05BankTransferConfirmationIntegrationTest {

    private static final String OWNER =
            "demo-owner-nola@senvia.local";

    private static final String OTHER_OWNER =
            "demo-owner-mori@senvia.local";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    @Autowired
    EntityManager entityManager;

    // Danh sách buyer do test PAY 05 tạo ra.
    // Dùng để cleanup sau mỗi test.
    private final List<String> createdBuyerEmails =
            new ArrayList<>();

    /**
     * Dọn dữ liệu sau mỗi test.
     *
     * @AfterEach vẫn chạy khi test thất bại
     * do assertion hoặc exception.
     *
     * Chỉ xóa dữ liệu thuộc buyer do PAY 05 tạo.
     * Không xóa tài khoản/shop demo có sẵn.
     */
    @AfterEach
    void cleanupPay05Fixtures() {
        try {
            for (String email : createdBuyerEmails) {

                // 1. Xóa payment của các đơn test.
                db.update("""
                        DELETE FROM payments
                        WHERE order_id IN (
                            SELECT id FROM orders
                            WHERE buyer_id IN (
                                SELECT id FROM users
                                WHERE email = ?
                            )
                        )
                        """, email);

                // 2. Xóa order items nếu có.
                db.update("""
                        DELETE FROM order_items
                        WHERE order_id IN (
                            SELECT id FROM orders
                            WHERE buyer_id IN (
                                SELECT id FROM users
                                WHERE email = ?
                            )
                        )
                        """, email);

                // 3. Xóa các đơn hàng của buyer test.
                db.update("""
                        DELETE FROM orders
                        WHERE buyer_id IN (
                            SELECT id FROM users
                            WHERE email = ?
                        )
                        """, email);

                // 4. Xóa buyer do test tạo.
                db.update(
                        "DELETE FROM users WHERE email = ?",
                        email
                );
            }
        } finally {
            createdBuyerEmails.clear();
        }
    }

    /**
     * TEST 1:
     * Chủ shop xác nhận thanh toán thành công.
     *
     * Payment: PENDING -> PAID.
     * paid_at được ghi nhận.
     * Order vẫn PENDING.
     * Mã tham chiếu không thay đổi.
     */
    @Test
    void ownBankTransferBecomesPaidWithoutConfirmingOrder()
            throws Exception {

        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/merchant/orders/detail?id=" + f.code()
                ))
                .andExpect(
                        flash().attributeExists("paymentSuccess")
                );

        assertEquals(
                "PAID",
                paymentStatus(f.id())
        );

        assertNotNull(
                paidAt(f.id())
        );

        assertEquals(
                "PENDING",
                orderStatus(f.id())
        );

        assertEquals(
                "SENVIA-" + f.code(),
                reference(f.id())
        );
    }

    @Test
    void sellerDetailShowsConfirmationAndFeedback() throws Exception {
        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        mvc.perform(get("/merchant/orders/detail")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Xác nhận đã nhận tiền (mô phỏng)")))
                .andExpect(content().string(containsString(
                        "/merchant/orders/payment/confirm")));

        MvcResult success = mvc.perform(post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        mvc.perform(get("/merchant/orders/detail")
                        .param("id", f.code())
                        .flashAttrs(success.getFlashMap())
                        .with(user(OWNER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Đã ghi nhận thanh toán mô phỏng.")))
                .andExpect(content().string(not(containsString(
                        "Xác nhận đã nhận tiền (mô phỏng)"))));

        MvcResult repeated = mvc.perform(post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        mvc.perform(get("/merchant/orders/detail")
                        .param("id", f.code())
                        .flashAttrs(repeated.getFlashMap())
                        .with(user(OWNER).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "Thanh toán không còn ở trạng thái chờ xác nhận.")));
    }

    @Test
    @Transactional
    void lockedShopOwnerCanConfirmExistingOrder() throws Exception {
        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );
        Long shopId = db.queryForObject(
                "SELECT shop_id FROM orders WHERE id = ?", Long.class, f.id());
        Long ownerId = db.queryForObject(
                "SELECT owner_id FROM shops WHERE id = ?", Long.class, shopId);

        // Giao dịch của test rollback cả thay đổi trạng thái shop sau khi chạy.
        db.update("""
                UPDATE shops
                SET status = 'LOCKED', lock_reason = 'Kiểm thử PAY 05',
                    locked_by = ?, locked_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, ownerId, shopId);

        mvc.perform(post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("paymentSuccess"));

        entityManager.flush();
        assertEquals("PAID", paymentStatus(f.id()));
        assertEquals("PENDING", orderStatus(f.id()));
    }

    /**
     * TEST 2:
     * Không cho xác nhận thanh toán lần thứ hai.
     *
     * Payment vẫn PAID.
     * paid_at không bị thay đổi.
     */
    @Test
    void secondConfirmationDoesNotChangePaidAt()
            throws Exception {

        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        // Xác nhận lần đầu.
        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf())
        )
                .andExpect(
                        flash().attributeExists("paymentSuccess")
                );

        Timestamp firstPaidAt = paidAt(f.id());

        assertNotNull(firstPaidAt);

        // Xác nhận lần thứ hai.
        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        flash().attributeExists("paymentError")
                );

        assertEquals(
                firstPaidAt,
                paidAt(f.id())
        );

        assertEquals(
                "PAID",
                paymentStatus(f.id())
        );
    }

    /**
     * TEST 3:
     * Chủ shop khác không được xác nhận
     * thanh toán của đơn không thuộc shop mình.
     */
    @Test
    void foreignOwnerCannotConfirm()
            throws Exception {

        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OTHER_OWNER).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().isNotFound());

        assertEquals(
                "PENDING",
                paymentStatus(f.id())
        );

        assertNull(
                paidAt(f.id())
        );
    }

    /**
     * TEST 4:
     * Buyer không được xác nhận thanh toán.
     */
    @Test
    void buyerCannotConfirm()
            throws Exception {

        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(f.buyerEmail()).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().isNotFound());

        assertEquals(
                "PENDING",
                paymentStatus(f.id())
        );

        assertNull(
                paidAt(f.id())
        );
    }

    /**
     * TEST 5:
     * Admin không được xác nhận.
     *
     * Request không có CSRF cũng bị từ chối.
     */
    @Test
    void adminAndMissingCsrfCannotConfirm()
            throws Exception {

        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        // Admin không được xác nhận.
        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(
                                user("admin@example.com")
                                        .roles("ADMIN")
                        )
                        .with(csrf())
        )
                .andExpect(status().isForbidden());

        // Thiếu CSRF bị chặn.
        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
        )
                .andExpect(status().isForbidden());

        assertEquals(
                "PENDING",
                paymentStatus(f.id())
        );

        assertNull(
                paidAt(f.id())
        );
    }

    /**
     * TEST 6:
     * Các trường hợp không được xác nhận:
     *
     * - Thanh toán COD.
     * - Đơn hàng đã hủy.
     * - Payment đã hoàn tiền.
     *
     * Không trường hợp nào được ghi paid_at.
     */
    @Test
    void codOrCancelledOrderCannotBeConfirmed()
            throws Exception {

        Fixture cod = createOrder(
                PaymentMethod.COD,
                PaymentStatus.COD_PENDING,
                OrderStatus.PENDING
        );

        Fixture cancelled = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.CANCELLED,
                OrderStatus.CANCELLED
        );

        Fixture refunded = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.REFUNDED,
                OrderStatus.PENDING
        );

        for (Fixture f : new Fixture[]{
                cod,
                cancelled,
                refunded
        }) {

            mvc.perform(
                    post("/merchant/orders/payment/confirm")
                            .param("id", f.code())
                            .with(user(OWNER).roles("USER"))
                            .with(csrf())
            )
                    .andExpect(status().is3xxRedirection())
                    .andExpect(
                            flash().attributeExists("paymentError")
                    );

            assertNull(
                    paidAt(f.id())
            );
        }

        // COD giữ nguyên trạng thái.
        assertEquals(
                "COD_PENDING",
                paymentStatus(cod.id())
        );

        // Payment đã hủy giữ nguyên.
        assertEquals(
                "CANCELLED",
                paymentStatus(cancelled.id())
        );

        // Payment đã hoàn tiền giữ nguyên.
        assertEquals(
                "REFUNDED",
                paymentStatus(refunded.id())
        );
    }

    @Test
    void pendingPaymentWithExistingPaymentOrRefundTimeCannotBeConfirmed() throws Exception {
        Fixture paidTime = createOrder(
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING, OrderStatus.PENDING);
        Fixture refundedTime = createOrder(
                PaymentMethod.BANK_TRANSFER, PaymentStatus.PENDING, OrderStatus.PENDING);
        Timestamp unexpectedTime = Timestamp.valueOf("2026-01-01 00:00:00");

        db.update("UPDATE payments SET paid_at = ? WHERE order_id = ?", unexpectedTime, paidTime.id());
        db.update("UPDATE payments SET refunded_at = ? WHERE order_id = ?", unexpectedTime, refundedTime.id());

        for (Fixture fixture : new Fixture[]{paidTime, refundedTime}) {
            var paymentBefore = db.queryForMap(
                    "SELECT status, paid_at, refunded_at FROM payments WHERE order_id = ?", fixture.id());

            mvc.perform(post("/merchant/orders/payment/confirm")
                            .param("id", fixture.code())
                            .with(user(OWNER).roles("USER"))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(flash().attributeExists("paymentError"));

            assertEquals(paymentBefore, db.queryForMap(
                    "SELECT status, paid_at, refunded_at FROM payments WHERE order_id = ?", fixture.id()));
            assertEquals("PENDING", orderStatus(fixture.id()));
        }
    }

    /**
     * TEST 7:
     * Endpoint xác nhận chỉ chấp nhận POST.
     *
     * GET không được làm thay đổi payment.
     */
    @Test
    void paymentConfirmationIsPostOnly()
            throws Exception {

        mvc.perform(
                get("/merchant/orders/payment/confirm")
                        .with(user(OWNER).roles("USER"))
        )
                .andExpect(status().isMethodNotAllowed());
    }

    /**
     * Tạo dữ liệu thật trong H2:
     *
     * 1. Lấy shop NOLA demo.
     * 2. Tạo buyer test.
     * 3. Tạo order cho buyer.
     * 4. Tạo payment cho order.
     *
     * Mỗi buyer có email UUID riêng.
     *
     * Đăng ký cleanup trước INSERT để
     * dữ liệu tạo dở vẫn được dọn sau test.
     */
    private Fixture createOrder(
            PaymentMethod method,
            PaymentStatus paymentStatus,
            OrderStatus orderStatus
    ) {

        // 1. Lấy shop của chủ NOLA.
        Long shopId = db.queryForObject("""
                SELECT s.id
                FROM shops s
                JOIN users u ON u.id = s.owner_id
                WHERE u.email = ?
                """,
                Long.class,
                OWNER
        );

        // 2. Tạo email buyer duy nhất.
        String buyerEmail =
                "pay05-buyer-"
                        + UUID.randomUUID()
                        + "@example.com";

        // QUAN TRỌNG:
        // Đăng ký cleanup trước INSERT.
        // Nếu quá trình tạo fixture thất bại giữa chừng,
        // @AfterEach vẫn biết buyer nào cần xóa.
        createdBuyerEmails.add(buyerEmail);

        // 3. Tạo buyer thật trong H2.
        db.update("""
                INSERT INTO users (
                    full_name,
                    email,
                    password_hash,
                    role,
                    account_status,
                    created_at,
                    updated_at
                )
                VALUES (
                    'Buyer PAY05',
                    ?,
                    'hash',
                    'USER',
                    'ACTIVE',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,
                buyerEmail
        );

        // 4. Lấy ID buyer vừa tạo.
        Long buyerId = db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                buyerEmail
        );

        // 5. Tạo mã đơn hàng duy nhất.
        String code =
                "ORD-PAY05-"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16);

        // 6. Tạo đơn hàng.
        db.update("""
                INSERT INTO orders (
                    order_code,
                    buyer_id,
                    shop_id,
                    status,
                    receiver_name,
                    receiver_phone,
                    shipping_address,
                    total_amount,
                    created_at,
                    updated_at
                )
                VALUES (
                    ?, ?, ?, ?,
                    'Người nhận',
                    '0900000000',
                    'Địa chỉ giao hàng',
                    ?,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,
                code,
                buyerId,
                shopId,
                orderStatus.name(),
                new BigDecimal("150000.00")
        );

        // 7. Lấy ID order vừa tạo.
        Long id = db.queryForObject(
                "SELECT id FROM orders WHERE order_code = ?",
                Long.class,
                code
        );

        // 8. Tạo payment cho order.
        db.update("""
                INSERT INTO payments (
                    order_id,
                    method,
                    amount,
                    status,
                    transaction_reference,
                    created_at
                )
                VALUES (
                    ?, ?, ?, ?, ?,
                    CURRENT_TIMESTAMP
                )
                """,
                id,
                method.name(),
                new BigDecimal("150000.00"),
                paymentStatus.name(),
                method == PaymentMethod.BANK_TRANSFER
                        ? "SENVIA-" + code
                        : null
        );

        // 9. Trả fixture cho test.
        return new Fixture(
                id,
                code,
                buyerEmail
        );
    }

    /**
     * Đọc trạng thái payment từ database.
     */
    private String paymentStatus(Long id) {
        return db.queryForObject(
                "SELECT status FROM payments WHERE order_id = ?",
                String.class,
                id
        );
    }

    /**
     * Đọc trạng thái order từ database.
     */
    private String orderStatus(Long id) {
        return db.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                id
        );
    }

    /**
     * Đọc thời điểm đã thanh toán.
     */
    private Timestamp paidAt(Long id) {
        return db.queryForObject(
                "SELECT paid_at FROM payments WHERE order_id = ?",
                Timestamp.class,
                id
        );
    }

    /**
     * Đọc mã tham chiếu chuyển khoản.
     */
    private String reference(Long id) {
        return db.queryForObject(
                "SELECT transaction_reference FROM payments WHERE order_id = ?",
                String.class,
                id
        );
    }

    /**
     * Dữ liệu fixture phục vụ test PAY 05.
     */
    private record Fixture(
            Long id,
            String code,
            String buyerEmail
    ) {
    }
}
