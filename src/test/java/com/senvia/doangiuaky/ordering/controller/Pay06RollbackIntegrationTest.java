
package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.merchant.api.MerchantStockApi;
import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class Pay06RollbackIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private Faults faults;

    // Chỉ dùng trong kiểm thử, không ảnh hưởng production.
    static class Faults {
        final AtomicBoolean failOnSecondDeduction =
                new AtomicBoolean();

        final AtomicBoolean failAfterCartDeletion =
                new AtomicBoolean();

        final AtomicBoolean cartDeletionWasExecuted =
                new AtomicBoolean();

        final AtomicInteger successfulDeductions =
                new AtomicInteger();

        void reset() {
            failOnSecondDeduction.set(false);
            failAfterCartDeletion.set(false);
            cartDeletionWasExecuted.set(false);
            successfulDeductions.set(0);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FaultConfiguration {

        @Bean
        Faults pay06Faults() {
            return new Faults();
        }

        // Bao quanh API kho thật để giả lập lỗi.
        @Bean
        @Primary
        MerchantStockApi merchantWithInjectedFault(
                @Qualifier("merchantStockService")
                MerchantStockApi real,
                Faults faults) {

            return new MerchantStockApi() {

                @Override
                public void deductForCheckout(
                        Long shopId,
                        Long productId,
                        int quantity,
                        BigDecimal expectedUnitPrice) {

                    // Thực hiện trừ kho thật.
                    real.deductForCheckout(
                            shopId,
                            productId,
                            quantity,
                            expectedUnitPrice
                    );

                    int count = faults.successfulDeductions
                            .incrementAndGet();

                    // Phát sinh lỗi sau khi trừ kho sản phẩm thứ 2.
                    if (faults.failOnSecondDeduction.get()
                            && count == 2) {

                        throw new IllegalStateException(
                                "PAY06: lỗi giả lập sau lần trừ kho thứ hai"
                        );
                    }
                }

                @Override
                public void restoreCancelledOrderItem(
                        Long orderItemId,
                        Long shopId,
                        Long productId,
                        int quantity) {

                    real.restoreCancelledOrderItem(
                            orderItemId,
                            shopId,
                            productId,
                            quantity
                    );
                }
            };
        }

        // Bao quanh API giỏ thật để giả lập lỗi sau khi xóa.
        @Bean
        @Primary
        CheckoutCartApi cartWithInjectedFault(
                @Qualifier("checkoutCartService")
                CheckoutCartApi real,
                Faults faults) {

            return new CheckoutCartApi() {

                @Override
                public List<CheckoutCartItem> getCheckoutSnapshot() {
                    return real.getCheckoutSnapshot();
                }

                @Override
                public void removePurchasedItems(
                        List<CheckoutCartItem> items) {

                    // Gọi xử lý thật: deleteAll và flush.
                    real.removePurchasedItems(items);

                    faults.cartDeletionWasExecuted.set(true);

                    // Lỗi xuất hiện SAU khi đã xóa trong transaction.
                    if (faults.failAfterCartDeletion.get()) {
                        throw new IllegalStateException(
                                "PAY06: lỗi giả lập sau khi xóa giỏ"
                        );
                    }
                }
            };
        }
    }

    @BeforeEach
    void beforeEach() {
        faults.reset();
    }

    @AfterEach
    void afterEach() {
        faults.reset();
    }

    // Dữ liệu dành riêng cho mỗi test.
    private record Fixture(
            String email,
            Long buyerId,
            Long cartId,
            Long toteId,
            Long lampId,
            Long unselectedId,
            Long toteItemId,
            Long lampItemId,
            int toteStockBefore,
            int lampStockBefore) {
    }

    private Fixture createFixture() {

        String email = "pay06-" + UUID.randomUUID()
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
                    'PAY06 Buyer',
                    ?,
                    'hash',
                    'USER',
                    'ACTIVE',
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """, email);

        Long buyer = db.queryForObject(
                "SELECT id FROM users WHERE email = ?",
                Long.class,
                email
        );

        // Sử dụng sản phẩm demo đã có.
        Long tote = productId("Canvas Tote Bag");
        Long lamp = productId("Brass Table Lamp");
        Long unselected = productId("Linen Shirt");

        // Xác nhận hai sản phẩm thuộc hai shop.
        Long toteShop = db.queryForObject(
                "SELECT shop_id FROM products WHERE id = ?",
                Long.class,
                tote
        );

        Long lampShop = db.queryForObject(
                "SELECT shop_id FROM products WHERE id = ?",
                Long.class,
                lamp
        );

        assertNotEquals(
                toteShop,
                lampShop,
                "Phải có hai shop để test rollback nhiều shop"
        );

        int toteStock = stock(tote);
        int lampStock = stock(lamp);

        assertTrue(toteStock > 0 && lampStock > 0);

        // Tạo giỏ cho người mua.
        db.update(
                "INSERT INTO carts (user_id) VALUES (?)",
                buyer
        );

        Long cart = db.queryForObject(
                "SELECT id FROM carts WHERE user_id = ?",
                Long.class,
                buyer
        );

        // Hai sản phẩm sẽ mua, một sản phẩm không chọn.
        for (Long product : List.of(tote, lamp, unselected)) {
            db.update(
                    """
                    INSERT INTO cart_items (
                        cart_id, product_id, quantity
                    )
                    VALUES (?, ?, 1)
                    """,
                    cart,
                    product
            );
        }

        Long toteItem = cartItemId(cart, tote);
        Long lampItem = cartItemId(cart, lamp);

        return new Fixture(
                email,
                buyer,
                cart,
                tote,
                lamp,
                unselected,
                toteItem,
                lampItem,
                toteStock,
                lampStock
        );
    }

    private Long productId(String name) {
        return db.queryForObject(
                "SELECT id FROM products WHERE name = ?",
                Long.class,
                name
        );
    }

    private Long cartItemId(Long cart, Long product) {
        return db.queryForObject(
                """
                SELECT id FROM cart_items
                WHERE cart_id = ? AND product_id = ?
                """,
                Long.class,
                cart,
                product
        );
    }

    private int stock(Long productId) {
        return db.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?",
                Integer.class,
                productId
        );
    }

    // Tạo bản xem trước hợp lệ và lấy checkoutToken.
    private String preview(
            Fixture f,
            MockHttpSession session) throws Exception {

        MvcResult result = mvc.perform(
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
                        .param("paymentMethod", "COD")
        ).andReturn();

        assertEquals(
                200,
                result.getResponse().getStatus()
        );

        Object token = result.getModelAndView()
                .getModel()
                .get("checkoutToken");

        assertInstanceOf(String.class, token);

        return (String) token;
    }

    // Gửi request xác nhận đặt hàng.
    private MvcResult confirm(
            Fixture f,
            MockHttpSession session,
            String token) throws Exception {

        return mvc.perform(
                post("/checkout/confirm")
                        .session(session)
                        .with(user(f.email()).roles("USER"))
                        .with(csrf())
                        .param("checkoutToken", token)
        ).andReturn();
    }

    @Test
    void secondShopDeductionFailureRollsBackFirstShopToo()
            throws Exception {

        Fixture f = createFixture();
        MockHttpSession session = new MockHttpSession();

        // Xem trước khi chưa bật giả lập lỗi.
        String token = preview(f, session);

        // Bật lỗi sau lần trừ kho thứ hai.
        faults.failOnSecondDeduction.set(true);

        MvcResult result = confirm(f, session, token);

        // Checkout phải bị từ chối.
        assertEquals(
                200,
                result.getResponse().getStatus()
        );

        assertNotNull(
                result.getModelAndView()
                        .getModel()
                        .get("checkoutError")
        );

        // Chứng minh cả hai lần trừ kho đã chạy.
        assertEquals(
                2,
                faults.successfulDeductions.get()
        );

        // Chưa đi tới bước xóa giỏ.
        assertFalse(
                faults.cartDeletionWasExecuted.get()
        );

        // Dữ liệu phải được rollback toàn bộ.
        assertCheckoutWasRolledBack(f);
    }

    @Test
    void errorAfterCartDeletionRollsBackOrdersPaymentsStockAndCart()
            throws Exception {

        Fixture f = createFixture();
        MockHttpSession session = new MockHttpSession();

        String token = preview(f, session);

        // Bật lỗi xảy ra sau khi xóa cart.
        faults.failAfterCartDeletion.set(true);

        MvcResult result = confirm(f, session, token);

        assertEquals(
                200,
                result.getResponse().getStatus()
        );

        assertNotNull(
                result.getModelAndView()
                        .getModel()
                        .get("checkoutError")
        );

        // Cả hai sản phẩm đã được xử lý trừ kho.
        assertEquals(
                2,
                faults.successfulDeductions.get()
        );

        // Xác nhận bước xóa giỏ đã được thực hiện.
        assertTrue(
                faults.cartDeletionWasExecuted.get()
        );

        // Nhưng tất cả phải được hoàn nguyên.
        assertCheckoutWasRolledBack(f);
    }

    // Kiểm tra toàn bộ kết quả rollback.
    private void assertCheckoutWasRolledBack(Fixture f) {

        // 1. Không còn order mới.
        assertEquals(
                0,
                db.queryForObject(
                        """
                        SELECT COUNT(*) FROM orders
                        WHERE buyer_id = ?
                        """,
                        Integer.class,
                        f.buyerId()
                )
        );

        // 2. Không còn order item.
        assertEquals(
                0,
                db.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM order_items i
                        JOIN orders o ON i.order_id = o.id
                        WHERE o.buyer_id = ?
                        """,
                        Integer.class,
                        f.buyerId()
                )
        );

        // 3. Không còn payment mới.
        assertEquals(
                0,
                db.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM payments p
                        JOIN orders o ON p.order_id = o.id
                        WHERE o.buyer_id = ?
                        """,
                        Integer.class,
                        f.buyerId()
                )
        );

        // 4. Tồn kho cả hai sản phẩm phải trở về ban đầu.
        assertEquals(
                f.toteStockBefore(),
                stock(f.toteId())
        );

        assertEquals(
                f.lampStockBefore(),
                stock(f.lampId())
        );

        // 5. Cả ba cart item vẫn tồn tại.
        assertEquals(
                3,
                db.queryForObject(
                        """
                        SELECT COUNT(*) FROM cart_items
                        WHERE cart_id = ?
                        """,
                        Integer.class,
                        f.cartId()
                )
        );

        // 6. Hai cart item được chọn vẫn còn.
        assertEquals(
                2,
                db.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM cart_items
                        WHERE id IN (?, ?) AND cart_id = ?
                        """,
                        Integer.class,
                        f.toteItemId(),
                        f.lampItemId(),
                        f.cartId()
                )
        );

        // 7. Sản phẩm không chọn cũng không bị ảnh hưởng.
        assertEquals(
                1,
                db.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM cart_items
                        WHERE cart_id = ? AND product_id = ?
                        """,
                        Integer.class,
                        f.cartId(),
                        f.unselectedId()
                )
        );
    }
}