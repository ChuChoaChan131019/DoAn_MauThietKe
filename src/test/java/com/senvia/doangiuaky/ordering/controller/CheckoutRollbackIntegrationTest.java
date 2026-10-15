
package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.service.OrderDraftWriter;
import com.senvia.doangiuaky.ordering.service.PreparedCheckout;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutRollbackIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate db;

    @Autowired
    FailureAfterSaveConfiguration failureConfiguration;

    @TestConfiguration(proxyBeanMethods = false)
    static class FailureAfterSaveConfiguration {
        private final AtomicBoolean savedBeforeFailure = new AtomicBoolean();

        @Bean
        @Primary
        OrderDraftWriter failingOrderDraftWriter(OrderRepository orders, OrderItemRepository items,
                PaymentRepository payments, MerchantApi merchantApi) {
            return new OrderDraftWriter(orders, items, payments, merchantApi) {
                @Override
                public List<Long> save(PreparedCheckout checkout, PaymentSeedProvider paymentSeedProvider) {
                    super.save(checkout, paymentSeedProvider);
                    savedBeforeFailure.set(true);
                    throw new IllegalStateException("Simulated failure after insert");
                }
            };
        }
    }

    @Test
    void errorAfterPersistingOrdersAndDeductingStockRollsBackEverything()
            throws Exception {

        String email = "rollback-checkout-"
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
                    'Rollback Buyer',
                    ?,
                    'hash',
                    'USER',
                    'ACTIVE',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """,
                email
        );

        Long buyer = db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                email
        );

        Long product = db.queryForObject(
                "SELECT id FROM products WHERE name = 'Canvas Tote Bag'",
                Long.class
        );

        int stock = db.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?",
                Integer.class,
                product
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
                """
                INSERT INTO cart_items (
                    cart_id,
                    product_id,
                    quantity
                )
                VALUES (?, ?, 1)
                """,
                cart,
                product
        );

        Long item = db.queryForObject(
                "SELECT id FROM cart_items WHERE cart_id = ?",
                Long.class,
                cart
        );

        MockHttpSession session = new MockHttpSession();

        // 1. Tạo bản xem trước hợp lệ.
        var preview = mvc.perform(
                    post("/checkout")
                            .session(session)
                            .with(user(email).roles("USER"))
                            .with(csrf())
                            .param("itemIds", item.toString())
                            .param("receiverName", "Buyer")
                            .param("receiverPhone", "0901234567")
                            .param("shippingAddress", "123 Test Street")
                            .param("paymentMethod", "COD")
                )
                .andReturn();

        String token = (String) preview
                .getModelAndView()
                .getModel()
                .get("checkoutToken");

        assertNotNull(token);

        // 2. Xác nhận đơn hàng; test bean phát sinh lỗi sau khi lưu thật.
        var result = mvc.perform(
                    post("/checkout/confirm")
                            .session(session)
                            .with(user(email).roles("USER"))
                            .with(csrf())
                            .param("checkoutToken", token)
                )
                .andReturn();

        // Controller hiển thị lỗi, không báo thành công.
        assertEquals(
                200,
                result.getResponse().getStatus()
        );
        assertTrue(failureConfiguration.savedBeforeFailure.get(),
                "Test phải đi qua bước lưu order/payment trước khi phát sinh lỗi.");

        // 4. Không có Order được lưu.
        assertEquals(
                0,
                db.queryForObject(
                        "SELECT COUNT(*) FROM orders WHERE buyer_id = ?",
                        Integer.class,
                        buyer
                )
        );

        // 5. Không có Payment được lưu.
        assertEquals(
                0,
                db.queryForObject("""
                        SELECT COUNT(*)
                        FROM payments p
                        JOIN orders o ON p.order_id = o.id
                        WHERE o.buyer_id = ?
                        """,
                        Integer.class,
                        buyer
                )
        );

        // 6. Kho phải được khôi phục.
        assertEquals(
                stock,
                db.queryForObject(
                        "SELECT stock_quantity FROM products WHERE id = ?",
                        Integer.class,
                        product
                )
        );

        // 7. Cart item vẫn tồn tại.
        assertEquals(
                1,
                db.queryForObject(
                        "SELECT COUNT(*) FROM cart_items WHERE cart_id = ?",
                        Integer.class,
                        cart
                )
        );
    }
}
