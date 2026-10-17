
package com.senvia.doangiuaky.ordering.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class Order02SellerAccessIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    private String nolaOrderCode;
    private String moriOrderCode;
    private Long buyerId;
    private String buyerEmail;

    @BeforeEach
    void prepareData() {
        buyerEmail = "order02-buyer-"
                + UUID.randomUUID()
                + "@example.com";

        db.update("""
                INSERT INTO users (
                    full_name, email, password_hash,
                    role, account_status, created_at, updated_at
                )
                VALUES (
                    'Order02 Buyer', ?, 'hash',
                    'USER', 'ACTIVE',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, buyerEmail);

        buyerId = db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                buyerEmail
        );

        Long nolaShopId = db.queryForObject(
                "SELECT id FROM shops WHERE shop_name = 'NOLA Store'",
                Long.class
        );

        Long moriShopId = db.queryForObject(
                "SELECT id FROM shops WHERE shop_name = 'MORI Store'",
                Long.class
        );

        nolaOrderCode = newOrderCode();
        moriOrderCode = newOrderCode();

        createOrder(nolaOrderCode, buyerId, nolaShopId);
        createOrder(moriOrderCode, buyerId, moriShopId);
    }

    @Test
    void nolaOwnerCannotSeeMoriOrder() throws Exception {
        mvc.perform(
                get("/merchant/orders")
                        .with(user("demo-owner-nola@senvia.local")
                                .roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(nolaOrderCode)
                ))
                .andExpect(content().string(
                        not(containsString(moriOrderCode))
                ));

        // Chủ NOLA được đọc đơn của NOLA.
        mvc.perform(
                get("/merchant/orders/detail")
                        .param("id", nolaOrderCode)
                        .with(user("demo-owner-nola@senvia.local")
                                .roles("USER"))
        )
                .andExpect(status().isOk());

        // Nhưng không thể đọc đơn của MORI.
        mvc.perform(
                get("/merchant/orders/detail")
                        .param("id", moriOrderCode)
                        .with(user("demo-owner-nola@senvia.local")
                                .roles("USER"))
        )
                .andExpect(status().isNotFound());
    }

    @Test
    void moriOwnerCannotSeeNolaOrder() throws Exception {
        mvc.perform(
                get("/merchant/orders")
                        .with(user("demo-owner-mori@senvia.local")
                                .roles("USER"))
        )
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(moriOrderCode)
                ))
                .andExpect(content().string(
                        not(containsString(nolaOrderCode))
                ));

        mvc.perform(
                get("/merchant/orders/detail")
                        .param("id", nolaOrderCode)
                        .with(user("demo-owner-mori@senvia.local")
                                .roles("USER"))
        )
                .andExpect(status().isNotFound());
    }

    @Test
    void lockedShopOwnerCanPageFilterAndReadExistingOrders() throws Exception {
            String sellerEmail = "order02-seller-" + UUID.randomUUID() + "@example.com";
            db.update("""
                            INSERT INTO users (
                                full_name, email, password_hash,
                                role, account_status, created_at, updated_at
                            )
                            VALUES ('Locked Shop Owner', ?, 'hash', 'USER', 'ACTIVE',
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                            """, sellerEmail);
            Long sellerId = db.queryForObject(
                            "SELECT id FROM users WHERE email = ?", Long.class, sellerEmail);
            db.update("""
                            INSERT INTO shops (
                                owner_id, shop_name, phone, address, status,
                                lock_reason, locked_by, locked_at
                            )
                            VALUES (?, ?, '0901234567', 'Địa chỉ kiểm thử', 'LOCKED',
                                    'Khóa để kiểm thử đơn cũ', ?, CURRENT_TIMESTAMP)
                            """, sellerId, "Order02 Shop " + UUID.randomUUID(), sellerId);
            Long lockedShopId = db.queryForObject(
                            "SELECT id FROM shops WHERE owner_id = ?", Long.class, sellerId);

            String oldestOrderCode = null;
            for (int i = 0; i < 11; i++) {
                    String code = newOrderCode();
                    if (i == 0) {
                            oldestOrderCode = code;
                    }
                    createOrder(code, buyerId, lockedShopId);
            }
            String confirmedOrderCode = newOrderCode();
            createOrder(confirmedOrderCode, buyerId, lockedShopId);
            db.update("UPDATE orders SET status = 'CONFIRMED' WHERE order_code = ?", confirmedOrderCode);

            mvc.perform(get("/merchant/orders")
                            .param("page", "2")
                            .with(user(sellerEmail).roles("USER")))
                            .andExpect(status().isOk())
                            .andExpect(model().attribute("currentPage", 2))
                            .andExpect(model().attribute("totalPages", 2))
                            .andExpect(model().attribute("totalOrders", 12L))
                            .andExpect(model().attribute("pendingCount", 11L))
                            .andExpect(model().attribute("orders", hasSize(2)))
                            .andExpect(content().string(containsString(oldestOrderCode)))
                            .andExpect(content().string(not(containsString(confirmedOrderCode))))
                            .andExpect(content().string(not(containsString(moriOrderCode))));

            mvc.perform(get("/merchant/orders")
                            .param("status", "CONFIRMED")
                            .with(user(sellerEmail).roles("USER")))
                            .andExpect(status().isOk())
                            .andExpect(model().attribute("totalOrders", 1L))
                            .andExpect(model().attribute("orders", hasSize(1)))
                            .andExpect(content().string(containsString(confirmedOrderCode)))
                            .andExpect(content().string(not(containsString(oldestOrderCode))));

            mvc.perform(get("/merchant/orders/detail")
                            .param("id", confirmedOrderCode)
                            .with(user(sellerEmail).roles("USER")))
                            .andExpect(status().isOk());
    }

    @Test
    void order09BuyerCannotReadOrCancelForeignOrder()
                    throws Exception {

            // Tạo Buyer khác với người sở hữu order.
            String otherEmail = "order09-other-"
                            + UUID.randomUUID()
                            + "@example.com";

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
                                'Other Buyer',
                                ?,
                                'hash',
                                'USER',
                                'ACTIVE',
                                CURRENT_TIMESTAMP,
                                CURRENT_TIMESTAMP
                            )
                            """,
                            otherEmail);

            mvc.perform(get("/account/orders")
                            .with(user(buyerEmail).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(nolaOrderCode)))
                    .andExpect(content().string(containsString(moriOrderCode)))
                    .andExpect(content().string(containsString(
                            "/account/orders/detail?id=" + nolaOrderCode)))
                    .andExpect(content().string(containsString(
                            "/account/orders/history?id=" + nolaOrderCode)))
                    .andExpect(content().string(containsString(
                            "/account/orders/history?id=" + moriOrderCode)));

            mvc.perform(get("/account/orders")
                            .with(user(otherEmail).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString(nolaOrderCode))))
                    .andExpect(content().string(not(containsString(moriOrderCode))));

            // Buyer thật được xem đơn mình.
            mvc.perform(
                            get("/account/orders/detail")
                                            .param("id", nolaOrderCode)
                                            .with(user(buyerEmail).roles("USER")))
                            .andExpect(status().isOk());

            mvc.perform(get("/account/orders/history")
                            .param("id", nolaOrderCode)
                            .with(user(buyerEmail).roles("USER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString(nolaOrderCode)));

            // Buyer khác tự thay URL để xem chi tiết.
            mvc.perform(
                            get("/account/orders/detail")
                                            .param("id", nolaOrderCode)
                                            .with(user(otherEmail).roles("USER")))
                            .andExpect(status().isNotFound());

            // Buyer khác không được xem lịch sử.
            mvc.perform(
                            get("/account/orders/history")
                                            .param("id", nolaOrderCode)
                                            .with(user(otherEmail).roles("USER")))
                            .andExpect(status().isNotFound());

            // Buyer khác gửi POST hủy đơn.
            mvc.perform(
                            post("/account/orders/cancel")
                                            .param("id", nolaOrderCode)
                                            .param("reason", "Cố tình hủy đơn người khác")
                                            .with(user(otherEmail).roles("USER"))
                                            .with(csrf()))
                            .andExpect(status().isNotFound());

            // Đơn không bị sửa.
            String currentStatus = db.queryForObject(
                            "SELECT status FROM orders WHERE order_code = ?",
                            String.class,
                            nolaOrderCode);

            assertEquals("PENDING", currentStatus);
    }

     @Test
    void order09ForeignSellerCannotReadOrMutateOrder()
                    throws Exception {

            String nolaOwner = "demo-owner-nola@senvia.local";

            // Chủ NOLA không xem được chi tiết đơn MORI.
            mvc.perform(
                            get("/merchant/orders/detail")
                                            .param("id", moriOrderCode)
                                            .with(user(nolaOwner).roles("USER")))
                            .andExpect(status().isNotFound());

            // Không được chuyển trạng thái đơn MORI.
            mvc.perform(
                            post("/merchant/orders/status")
                                            .param("id", moriOrderCode)
                                            .param("status", "CONFIRMED")
                                            .with(user(nolaOwner).roles("USER"))
                                            .with(csrf()))
                            .andExpect(status().isNotFound());

            // Không được xác nhận payment đơn MORI.
            mvc.perform(
                            post("/merchant/orders/payment/confirm")
                                            .param("id", moriOrderCode)
                                            .with(user(nolaOwner).roles("USER"))
                                            .with(csrf()))
                            .andExpect(status().isNotFound());

            // Không được hủy đơn MORI.
            mvc.perform(
                            post("/merchant/orders/cancel")
                                            .param("id", moriOrderCode)
                                            .param("reason", "Không có quyền")
                                            .with(user(nolaOwner).roles("USER"))
                                            .with(csrf()))
                            .andExpect(status().isNotFound());

            // Trạng thái đơn và payment không thay đổi.
            String orderStatus = db.queryForObject(
                            "SELECT status FROM orders WHERE order_code = ?",
                            String.class,
                            moriOrderCode);

            String paymentStatus = db.queryForObject(
                            """
                                            SELECT p.status
                                            FROM payments p
                                            JOIN orders o ON o.id = p.order_id
                                            WHERE o.order_code = ?
                                            """,
                            String.class,
                            moriOrderCode);

            assertEquals("PENDING", orderStatus);
            assertEquals("COD_PENDING", paymentStatus);
    }

    @Test
    void order09NormalUserCannotAccessAdminOrderDetail()
                    throws Exception {

            mvc.perform(get("/admin/orders")
                            .with(user(buyerEmail).roles("USER")))
                    .andExpect(status().isForbidden());

            mvc.perform(
                            get("/admin/orders/detail")
                                            .param("id", nolaOrderCode)
                                            .with(user(buyerEmail).roles("USER")))
                            .andExpect(status().isForbidden());

            mvc.perform(
                            get("/admin/orders/history")
                                            .param("id", nolaOrderCode)
                                            .with(user(buyerEmail).roles("USER")))
                            .andExpect(status().isForbidden());
    }

    @Test
    void order09AdminListUsesRealOrdersAndLinksToDetailAndHistory() throws Exception {
        String adminEmail = "order09-admin-" + UUID.randomUUID() + "@example.com";
        db.update("""
                INSERT INTO users (
                    full_name, email, password_hash,
                    role, account_status, created_at, updated_at
                )
                VALUES ('Order09 Admin', ?, 'hash', 'ADMIN', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, adminEmail);

        mvc.perform(get("/admin/orders")
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nolaOrderCode)))
                .andExpect(content().string(containsString(moriOrderCode)))
                .andExpect(content().string(containsString(
                        "/admin/orders/detail?id=" + nolaOrderCode)))
                .andExpect(content().string(containsString(
                        "/admin/orders/history?id=" + nolaOrderCode)))
                .andExpect(content().string(containsString(
                        "/admin/orders/history?id=" + moriOrderCode)));

        mvc.perform(get("/admin/orders/detail")
                        .param("id", nolaOrderCode)
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nolaOrderCode)));

        mvc.perform(get("/admin/orders/history")
                        .param("id", nolaOrderCode)
                        .with(user(adminEmail).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(nolaOrderCode)));
    }

    private void createOrder(
            String orderCode,
            Long buyerId,
            Long shopId
    ) {
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
                VALUES (?, ?, ?, 'PENDING', ?, ?, ?, ?,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                orderCode,
                buyerId,
                shopId,
                "Order02 Buyer",
                "0901234567",
                "Địa chỉ kiểm thử",
                120000
        );

        Long orderId = db.queryForObject(
                "SELECT id FROM orders WHERE order_code = ?",
                Long.class,
                orderCode
        );

        db.update("""
                INSERT INTO payments (
                    order_id,
                    method,
                    amount,
                    status,
                    created_at
                )
                VALUES (?, 'COD', 120000, 'COD_PENDING',
                        CURRENT_TIMESTAMP)
                """, orderId);
    }

    private String newOrderCode() {
        return "O02-" + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 20);
    }
}
