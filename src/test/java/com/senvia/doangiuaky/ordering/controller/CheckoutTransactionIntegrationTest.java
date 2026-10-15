
package com.senvia.doangiuaky.ordering.controller;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutTransactionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    record Fixture(
            String email,
            Long buyerId,
            Long cartId,
            Long toteId,
            Long lampId,
            Long unselectedId,
            Long toteItemId,
            Long lampItemId,
            int toteBefore,
            int lampBefore
    ) {
    }

    Fixture fixture() {

        String email = "real-checkout-"
                + UUID.randomUUID()
                + "@example.com";

        db.update("""
                INSERT INTO users (
                    full_name, email, password_hash,
                    role, account_status, created_at, updated_at
                )
                VALUES (
                    'Checkout User', ?, 'hash',
                    'USER', 'ACTIVE',
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                )
                """, email);

        Long buyer = db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                email
        );

        Long tote = db.queryForObject(
                "SELECT id FROM products WHERE name = 'Canvas Tote Bag'",
                Long.class
        );

        Long lamp = db.queryForObject(
                "SELECT id FROM products WHERE name = 'Brass Table Lamp'",
                Long.class
        );

        Long unselected = db.queryForObject(
                "SELECT id FROM products WHERE name = 'Linen Shirt'",
                Long.class
        );

        db.update(
                "INSERT INTO carts (user_id) VALUES (?)",
                buyer
        );

        Long cart = db.queryForObject(
                "SELECT id FROM carts WHERE user_id = ?",
                Long.class,
                buyer
        );

        db.update(
                "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 1)",
                cart, tote
        );

        db.update(
                "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 1)",
                cart, lamp
        );

        db.update(
                "INSERT INTO cart_items (cart_id, product_id, quantity) VALUES (?, ?, 1)",
                cart, unselected
        );

        Long toteItem = db.queryForObject(
                "SELECT id FROM cart_items WHERE cart_id = ? AND product_id = ?",
                Long.class,
                cart,
                tote
        );

        Long lampItem = db.queryForObject(
                "SELECT id FROM cart_items WHERE cart_id = ? AND product_id = ?",
                Long.class,
                cart,
                lamp
        );

        return new Fixture(
                email,
                buyer,
                cart,
                tote,
                lamp,
                unselected,
                toteItem,
                lampItem,
                stock(tote),
                stock(lamp)
        );
    }

    MvcResult preview(
            Fixture f,
            String method,
            MockHttpSession session
    ) throws Exception {

        return mvc.perform(
                    post("/checkout")
                            .session(session)
                            .with(user(f.email()).roles("USER"))
                            .with(csrf())
                            .param(
                                    "itemIds",
                                    f.toteItemId().toString(),
                                    f.lampItemId().toString()
                            )
                            .param("receiverName", "Buyer")
                            .param("receiverPhone", "0901234567")
                            .param("shippingAddress", "123 Test Street")
                            .param("paymentMethod", method)
                )
                .andExpect(status().isOk())
                .andExpect(
                        model().attributeExists(
                                "checkoutToken",
                                "checkoutPreview"
                        )
                )
                .andReturn();
    }

    MvcResult confirm(
            Fixture f,
            MockHttpSession session,
            String token
    ) throws Exception {

        return mvc.perform(
                    post("/checkout/confirm")
                            .session(session)
                            .with(user(f.email()).roles("USER"))
                            .with(csrf())
                            .param("checkoutToken", token)
                )
                .andReturn();
    }

    @Test
    void codCreatesTwoOrdersTwoPaymentsAndDeductsStockAndCart()
            throws Exception {

        assertPurchase("COD", "COD_PENDING");
    }

    @Test
    void bankTransferCreatesPendingPaymentsWithDistinctReferences()
            throws Exception {

        assertPurchase("BANK_TRANSFER", "PENDING");
    }

    @Test
    void checkoutSucceedsWhenItConsumesAllAvailableStock()
            throws Exception {

        Fixture f = fixture();
        MockHttpSession session = new MockHttpSession();

        try {
            db.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", f.toteId());
            db.update("UPDATE products SET stock_quantity = 1 WHERE id = ?", f.lampId());

            String token = (String) preview(f, "COD", session)
                    .getModelAndView()
                    .getModel()
                    .get("checkoutToken");

            MvcResult result = confirm(f, session, token);

            assertEquals(302, result.getResponse().getStatus());
            assertEquals("/checkout/success", result.getResponse().getRedirectedUrl());
            assertEquals(0, stock(f.toteId()));
            assertEquals(0, stock(f.lampId()));
            assertEquals(2, db.queryForObject(
                    "SELECT COUNT(*) FROM orders WHERE buyer_id = ?", Integer.class, f.buyerId()));
            assertEquals(2, db.queryForObject(
                    "SELECT COUNT(*) FROM payments p JOIN orders o ON o.id = p.order_id WHERE o.buyer_id = ?",
                    Integer.class, f.buyerId()));
        } finally {
            db.update("UPDATE products SET stock_quantity = ? WHERE id = ?", f.toteBefore(), f.toteId());
            db.update("UPDATE products SET stock_quantity = ? WHERE id = ?", f.lampBefore(), f.lampId());
        }
    }

    void assertPurchase(
            String method,
            String expectedStatus
    ) throws Exception {

        Fixture f = fixture();
        MockHttpSession session = new MockHttpSession();

        String token = (String) preview(
                f, method, session
        ).getModelAndView()
                .getModel()
                .get("checkoutToken");

        MvcResult result = confirm(f, session, token);

        assertEquals(
                302,
                result.getResponse().getStatus()
        );

        assertEquals(
                "/checkout/success",
                result.getResponse().getRedirectedUrl()
        );

        // Mỗi shop phải có một Order.
        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE buyer_id = ?",
                        Integer.class,
                        f.buyerId()
                )
        );

        // Phải có hai OrderItem.
        assertEquals(
                2,
                db.queryForObject("""
                        SELECT COUNT(*)
                        FROM order_items i
                        JOIN orders o ON o.id = i.order_id
                        WHERE o.buyer_id = ?
                        """,
                        Integer.class,
                        f.buyerId()
                )
        );

        // Mỗi Order phải có một Payment.
        assertEquals(
                2,
                db.queryForObject("""
                        SELECT COUNT(*)
                        FROM payments p
                        JOIN orders o ON o.id = p.order_id
                        WHERE o.buyer_id = ?
                        """,
                        Integer.class,
                        f.buyerId()
                )
        );

        // Payment phải giữ đúng tổng tiền Order.
        assertEquals(
                0,
                db.queryForObject("""
                        SELECT COUNT(*)
                        FROM payments p
                        JOIN orders o ON o.id = p.order_id
                        WHERE o.buyer_id = ?
                          AND p.amount <> o.total_amount
                        """,
                        Integer.class,
                        f.buyerId()
                )
        );

        // Kiểm tra trạng thái payment.
        List<String> statuses = db.queryForList("""
                SELECT p.status
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                WHERE o.buyer_id = ?
                ORDER BY o.id
                """,
                String.class,
                f.buyerId()
        );

        assertEquals(
                List.of(expectedStatus, expectedStatus),
                statuses
        );

        List<String> methods = db.queryForList("""
                SELECT p.method
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                WHERE o.buyer_id = ?
                ORDER BY o.id
                """,
                String.class,
                f.buyerId()
        );

        assertEquals(List.of(method, method), methods);

        // Kiểm tra mã tham chiếu.
        List<String> references = db.queryForList("""
                SELECT p.transaction_reference
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                WHERE o.buyer_id = ?
                ORDER BY o.id
                """,
                String.class,
                f.buyerId()
        );

        if (method.equals("COD")) {

            assertEquals(2, references.size());

            assertNull(references.get(0));
            assertNull(references.get(1));

        } else {

            assertNotNull(references.get(0));
            assertNotNull(references.get(1));

            assertNotEquals(
                    references.get(0),
                    references.get(1)
            );

            assertTrue(
                    references.stream()
                            .allMatch(
                                    x -> x.startsWith("SENVIA-ORD-")
                            )
            );
        }

        // Kiểm tra đã trừ kho.
        assertEquals(
                f.toteBefore() - 1,
                stock(f.toteId())
        );

        assertEquals(
                f.lampBefore() - 1,
                stock(f.lampId())
        );

        // Chỉ còn lại sản phẩm không được chọn.
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?",
                        Integer.class,
                        f.cartId()
                )
        );

        assertEquals(
                f.unselectedId(),
                db.queryForObject(
                        "SELECT product_id FROM cart_items WHERE cart_id = ?",
                        Long.class,
                        f.cartId()
                )
        );

        // Gửi lại token cũ không được tạo thêm đơn.
        assertEquals(
                302,
                confirm(
                        f, session, token
                ).getResponse().getStatus()
        );

        assertEquals(
                2,
                db.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE buyer_id = ?",
                        Integer.class,
                        f.buyerId()
                )
        );
    }

    @Test
    void priceChangeRequiresASecondPreviewWithoutCreatingOrders()
            throws Exception {

        Fixture f = fixture();
        MockHttpSession session = new MockHttpSession();

        String token = (String) preview(
                f, "COD", session
        ).getModelAndView()
                .getModel()
                .get("checkoutToken");

        BigDecimal originalPrice = db.queryForObject(
                "SELECT price FROM products WHERE id = ?",
                BigDecimal.class,
                f.toteId()
        );

        try {

            db.update(
                    "UPDATE products SET price = ? WHERE id = ?",
                    originalPrice.add(
                            new BigDecimal("1000.00")
                    ),
                    f.toteId()
            );

            MvcResult result = confirm(
                    f, session, token
            );

            assertEquals(
                    200,
                    result.getResponse().getStatus()
            );

            assertNotNull(
                    result.getModelAndView()
                            .getModel()
                            .get("checkoutError")
            );

            String refreshedToken = (String) result
                    .getModelAndView()
                    .getModel()
                    .get("checkoutToken");

            assertNotNull(refreshedToken);
            assertNotEquals(token, refreshedToken);

            assertEquals(
                    0,
                    db.queryForObject(
                            "SELECT COUNT(*) FROM orders WHERE buyer_id = ?",
                            Integer.class,
                            f.buyerId()
                    )
            );

            assertEquals(
                    f.toteBefore(),
                    stock(f.toteId())
            );

            assertEquals(
                    f.lampBefore(),
                    stock(f.lampId())
            );

        } finally {

            db.update(
                    "UPDATE products SET price = ? WHERE id = ?",
                    originalPrice,
                    f.toteId()
            );
        }
    }

    @Test
    void insufficientStockNeverCreatesOrdersOrDeletesCart()
            throws Exception {

        Fixture f = fixture();
        MockHttpSession session = new MockHttpSession();

        String token = (String) preview(
                f, "COD", session
        ).getModelAndView()
                .getModel()
                .get("checkoutToken");

        try {

            db.update(
                    "UPDATE products SET stock_quantity = 0 WHERE id = ?",
                    f.lampId()
            );

            MvcResult result = confirm(
                    f, session, token
            );

            assertEquals(
                    200,
                    result.getResponse().getStatus()
            );

            assertEquals(
                    0,
                    db.queryForObject(
                            "SELECT COUNT(*) FROM orders WHERE buyer_id = ?",
                            Integer.class,
                            f.buyerId()
                    )
            );

            assertEquals(
                    3,
                    db.queryForObject(
                            "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?",
                            Integer.class,
                            f.cartId()
                    )
            );

            assertEquals(
                    f.toteBefore(),
                    stock(f.toteId())
            );

        } finally {

            db.update(
                    "UPDATE products SET stock_quantity = ? WHERE id = ?",
                    f.lampBefore(),
                    f.lampId()
            );
        }
    }

    int stock(Long productId) {
        return db.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?",
                Integer.class,
                productId
        );
    }
}
