
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

    // Danh sách buyer được tạo trong quá trình test.
    private final List<String> createdBuyerEmails =
            new ArrayList<>();

    /**
     * Dọn dữ liệu sau mỗi test.
     *
     * Chỉ xóa dữ liệu của buyer do PAY 05 tạo.
     * Không xóa tài khoản hoặc shop demo.
     */
    @AfterEach
    void cleanupPay05Fixtures() {
        try {
            for (String email : createdBuyerEmails) {

                // ORDER 07:
                // Xóa lịch sử trước để tránh lỗi khóa ngoại.
                db.update("""
                        DELETE FROM order_status_histories
                        WHERE order_id IN (
                            SELECT o.id
                            FROM orders o
                            JOIN users u ON u.id = o.buyer_id
                            WHERE u.email = ?
                        )
                        """, email);

                // Xóa payment.
                db.update("""
                        DELETE FROM payments
                        WHERE order_id IN (
                            SELECT o.id
                            FROM orders o
                            JOIN users u ON u.id = o.buyer_id
                            WHERE u.email = ?
                        )
                        """, email);

                // Xóa order items nếu có.
                db.update("""
                        DELETE FROM order_items
                        WHERE order_id IN (
                            SELECT o.id
                            FROM orders o
                            JOIN users u ON u.id = o.buyer_id
                            WHERE u.email = ?
                        )
                        """, email);

                // Xóa đơn hàng.
                db.update("""
                        DELETE FROM orders
                        WHERE buyer_id IN (
                            SELECT id
                            FROM users
                            WHERE email = ?
                        )
                        """, email);

                // Xóa buyer.
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
     * TEST 1 - PAY 05 + ORDER 07
     *
     * Chủ shop xác nhận thanh toán thành công.
     *
     * Payment: PENDING -> PAID.
     * paid_at được ghi nhận.
     * Order vẫn PENDING.
     * Mã tham chiếu không thay đổi.
     *
     * ORDER 07:
     * Xác nhận payment không tạo thêm history của order.
     */
    @Test
    void ownBankTransferBecomesPaidWithoutConfirmingOrder()
            throws Exception {

        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        // ORDER 07:
        // Tạo history khởi tạo để kiểm tra PAY 05
        // không sinh thêm mốc lịch sử trạng thái đơn.
        Long buyerId = db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                f.buyerEmail()
        );

        db.update("""
                INSERT INTO order_status_histories (
                    order_id,
                    old_status,
                    new_status,
                    changed_by,
                    note
                )
                VALUES (
                    ?, NULL, 'PENDING', ?,
                    'Khởi tạo đơn hàng'
                )
                """,
                f.id(),
                buyerId
        );

        // Đếm history trước khi xác nhận payment.
        Long historyBefore = historyCount(f.id());

        assertEquals(
                1L,
                historyBefore,
                "Đơn phải có một mốc lịch sử khởi tạo."
        );

        // Chủ shop xác nhận đã nhận tiền.
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

        // Payment được chuyển sang PAID.
        assertEquals(
                "PAID",
                paymentStatus(f.id())
        );

        // Có thời điểm thanh toán.
        assertNotNull(
                paidAt(f.id())
        );

        // Order không tự động chuyển sang CONFIRMED.
        assertEquals(
                "PENDING",
                orderStatus(f.id())
        );

        // Mã tham chiếu giữ nguyên.
        assertEquals(
                "SENVIA-" + f.code(),
                reference(f.id())
        );

        // ORDER 07:
        // Đếm lịch sử sau khi xác nhận payment.
        Long historyAfter = historyCount(f.id());

        assertEquals(
                historyBefore,
                historyAfter,
                "Xác nhận payment không được tạo history của order."
        );

        // Kiểm tra mốc lịch sử ban đầu không bị sửa.
        String latestStatus = db.queryForObject("""
                SELECT new_status
                FROM order_status_histories
                WHERE order_id = ?
                ORDER BY changed_at DESC, id DESC
                LIMIT 1
                """,
                String.class,
                f.id()
        );

        assertEquals(
                "PENDING",
                latestStatus,
                "PAY 05 không được thay đổi lịch sử trạng thái đơn."
        );
    }

    /**
     * TEST 2
     *
     * Kiểm tra trang chi tiết shop.
     * Hiển thị nút xác nhận payment.
     * Sau xác nhận có thông báo thành công.
     * Xác nhận lặp có thông báo lỗi.
     */
    @Test
    void sellerDetailShowsConfirmationAndFeedback()
            throws Exception {

        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        // Trước khi thanh toán.
        mvc.perform(
                get("/merchant/orders/detail")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(
                        content().string(
                                containsString(
                                        "Xác nhận đã nhận tiền (mô phỏng)"
                                )
                        )
                )
                .andExpect(
                        content().string(
                                containsString(
                                        "/merchant/orders/payment/confirm"
                                )
                        )
                );

        // Xác nhận payment lần đầu.
        MvcResult success = mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // Kiểm tra giao diện sau khi xác nhận.
        mvc.perform(
                get("/merchant/orders/detail")
                        .param("id", f.code())
                        .flashAttrs(success.getFlashMap())
                        .with(user(OWNER).roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(
                        content().string(
                                containsString(
                                        "Đã ghi nhận thanh toán mô phỏng."
                                )
                        )
                )
                .andExpect(
                        content().string(
                                not(containsString(
                                        "Xác nhận đã nhận tiền (mô phỏng)"
                                ))
                        )
                );

        // Gửi xác nhận lần thứ hai.
        MvcResult repeated = mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // Giao diện hiển thị lỗi xác nhận lặp.
        mvc.perform(
                get("/merchant/orders/detail")
                        .param("id", f.code())
                        .flashAttrs(repeated.getFlashMap())
                        .with(user(OWNER).roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(
                        content().string(
                                containsString(
                                        "Thanh toán không còn ở trạng thái chờ xác nhận."
                                )
                        )
                );
    }

    /**
     * TEST 3
     *
     * Chủ shop LOCKED vẫn được xác nhận payment
     * của đơn hàng đã tồn tại.
     */
    @Test
    @Transactional
    void lockedShopOwnerCanConfirmExistingOrder()
            throws Exception {

        Fixture f = createOrder(
                PaymentMethod.BANK_TRANSFER,
                PaymentStatus.PENDING,
                OrderStatus.PENDING
        );

        Long shopId = db.queryForObject(
                "SELECT shop_id FROM orders WHERE id = ?",
                Long.class,
                f.id()
        );

        Long ownerId = db.queryForObject(
                "SELECT owner_id FROM shops WHERE id = ?",
                Long.class,
                shopId
        );

        // Transaction của test rollback việc khóa shop.
        db.update("""
                UPDATE shops
                SET status = 'LOCKED',
                    lock_reason = 'Kiểm thử PAY 05',
                    locked_by = ?,
                    locked_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                ownerId,
                shopId
        );

        mvc.perform(
                post("/merchant/orders/payment/confirm")
                        .param("id", f.code())
                        .with(user(OWNER).roles("USER"))
                        .with(csrf())
        )
                .andExpect(status().is3xxRedirection())
                .andExpect(
                        flash().attributeExists("paymentSuccess")
                );

        entityManager.flush();

        assertEquals(
                "PAID",
                paymentStatus(f.id())
        );

        assertEquals(
                "PENDING",
                orderStatus(f.id())
        );
    }

    /**
     * TEST 4
     *
     * Không cho xác nhận thanh toán lần thứ hai.
     * paid_at phải giữ nguyên.
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
     * TEST 5
     *
     * Chủ shop khác không được xác nhận payment.
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
     * TEST 6
     *
     * Buyer không được xác nhận payment.
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
     * TEST 7
     *
     * Admin không được xác nhận payment.
     * Request không có CSRF cũng phải bị chặn.
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

        // Thiếu CSRF.
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
     * TEST 8
     *
     * Không cho xác nhận:
     * - COD.
     * - Order đã hủy.
     * - Payment đã REFUNDED.
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

        assertEquals(
                "COD_PENDING",
                paymentStatus(cod.id())
        );

        assertEquals(
                "CANCELLED",
                paymentStatus(cancelled.id())
        );

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
     * TEST 9
     *
     * Xác nhận payment chỉ chấp nhận POST.
     * GET không được thay đổi dữ liệu.
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
     * Tạo fixture PAY 05 trong H2.
     *
     * 1. Lấy shop demo của NOLA.
     * 2. Tạo buyer.
     * 3. Tạo order.
     * 4. Tạo payment.
     *
     * Không tạo history mặc định tại đây.
     * Test ORDER 07 sẽ chủ động tạo mốc cần kiểm tra.
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

        // 2. Email buyer duy nhất.
        String buyerEmail =
                "pay05-buyer-"
                        + UUID.randomUUID()
                        + "@example.com";

        // Đăng ký cleanup trước INSERT.
        createdBuyerEmails.add(buyerEmail);

        // 3. Tạo buyer.
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

        // 4. Lấy ID buyer.
        Long buyerId = db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                buyerEmail
        );

        // 5. Tạo mã đơn hàng.
        String code =
                "ORD-PAY05-"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 16);

        // 6. Tạo order.
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

        // 7. Lấy ID order.
        Long id = db.queryForObject(
                "SELECT id FROM orders WHERE order_code = ?",
                Long.class,
                code
        );

        // 8. Tạo payment.
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

        // 9. Trả fixture.
        return new Fixture(
                id,
                code,
                buyerEmail
        );
    }

    /**
     * Đọc trạng thái payment.
     */
    private String paymentStatus(Long id) {
        return db.queryForObject(
                "SELECT status FROM payments WHERE order_id = ?",
                String.class,
                id
        );
    }

    /**
     * Đọc trạng thái order.
     */
    private String orderStatus(Long id) {
        return db.queryForObject(
                "SELECT status FROM orders WHERE id = ?",
                String.class,
                id
        );
    }

    /**
     * Đọc thời gian đã thanh toán.
     */
    private Timestamp paidAt(Long id) {
        return db.queryForObject(
                "SELECT paid_at FROM payments WHERE order_id = ?",
                Timestamp.class,
                id
        );
    }

    /**
     * Đọc mã tham chiếu.
     */
    private String reference(Long id) {
        return db.queryForObject(
                "SELECT transaction_reference FROM payments WHERE order_id = ?",
                String.class,
                id
        );
    }

    /**
     * ORDER 07:
     * Đếm số bản ghi lịch sử trạng thái của order.
     */
    private Long historyCount(Long orderId) {
        return db.queryForObject(
                """
                SELECT COUNT(*)
                FROM order_status_histories
                WHERE order_id = ?
                """,
                Long.class,
                orderId
        );
    }

    /**
     * Dữ liệu phục vụ các test PAY 05.
     */
    private record Fixture(
            Long id,
            String code,
            String buyerEmail
    ) {
    }
}