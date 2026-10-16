
package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.merchant.api.MerchantStockApi;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class Pay06ConcurrentCheckoutIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private StockGate gate;

    /*
     * Đồng bộ hai checkout ngay trước khi trừ kho.
     *
     * Mục đích:
     * - Cả hai transaction phải đi đến bước trừ kho.
     * - Sau đó mới cho cả hai cùng tranh chấp sản phẩm.
     * - Không thay đổi logic production.
     */
    static class StockGate {

        final AtomicBoolean enabled = new AtomicBoolean(false);

        final AtomicInteger arrivals = new AtomicInteger(0);

        final CyclicBarrier barrier = new CyclicBarrier(2);

        void awaitBoth() {
            arrivals.incrementAndGet();

            try {
                barrier.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(
                        "PAY06: test bị ngắt khi đợi checkout",
                        ex
                );
            } catch (BrokenBarrierException | TimeoutException ex) {
                throw new IllegalStateException(
                        "PAY06: hai checkout không đến được bước trừ kho",
                        ex
                );
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ConcurrencyConfiguration {

        @Bean
        StockGate pay06StockGate() {
            return new StockGate();
        }

        @Bean
        @Primary
        MerchantStockApi gatedMerchantStock(
                @Qualifier("merchantStockService")
                MerchantStockApi real,
                StockGate gate) {

            return new MerchantStockApi() {

                @Override
                public void deductForCheckout(
                        Long shopId,
                        Long productId,
                        int quantity,
                        BigDecimal expectedUnitPrice) {

                    if (gate.enabled.get()) {
                        gate.awaitBoth();
                    }

                    // Thực hiện nghiệp vụ trừ kho thật.
                    real.deductForCheckout(
                            shopId,
                            productId,
                            quantity,
                            expectedUnitPrice
                    );
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
    }

    record Buyer(
            String email,
            Long userId,
            Long cartId,
            Long cartItemId,
            MockHttpSession session
    ) {}

    private int stock(Long productId) {
        return db.queryForObject(
                """
                SELECT stock_quantity
                FROM products
                WHERE id = ?
                """,
                Integer.class,
                productId
        );
    }

    private Long productId(String name) {
        return db.queryForObject(
                """
                SELECT id
                FROM products
                WHERE name = ?
                """,
                Long.class,
                name
        );
    }

    /*
     * Tạo một người mua độc lập với:
     * - User riêng
     * - Cart riêng
     * - Cart item riêng
     * - HTTP session riêng
     */
    private Buyer createBuyer(Long productId) {

        String email = "pay06-concurrent-"
                + UUID.randomUUID()
                + "@example.com";

        db.update(
                """
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
                    'Concurrent Buyer',
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

        Long buyerId = db.queryForObject(
                """
                SELECT id FROM users WHERE email = ?
                """,
                Long.class,
                email
        );

        db.update(
                """
                INSERT INTO carts (user_id)
                VALUES (?)
                """,
                buyerId
        );

        Long cartId = db.queryForObject(
                """
                SELECT id
                FROM carts
                WHERE user_id = ?
                """,
                Long.class,
                buyerId
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
                cartId,
                productId
        );

        Long cartItemId = db.queryForObject(
                """
                SELECT id
                FROM cart_items
                WHERE cart_id = ?
                  AND product_id = ?
                """,
                Long.class,
                cartId,
                productId
        );

        return new Buyer(
                email,
                buyerId,
                cartId,
                cartItemId,
                new MockHttpSession()
        );
    }

    /*
     * Tạo bản xem trước.
     * Cả hai buyer phải xem trước thành công
     * trước khi bắt đầu xác nhận đồng thời.
     */
    private String preview(Buyer buyer) throws Exception {

        MvcResult result = mvc.perform(
                post("/checkout")
                        .session(buyer.session())
                        .with(user(buyer.email()).roles("USER"))
                        .with(csrf())
                        .param(
                                "itemIds",
                                buyer.cartItemId().toString()
                        )
                        .param("receiverName", "Buyer")
                        .param("receiverPhone", "0901234567")
                        .param(
                                "shippingAddress",
                                "123 Concurrent Test Street"
                        )
                        .param("paymentMethod", "COD")
        ).andReturn();

        assertEquals(
                200,
                result.getResponse().getStatus(),
                "Bước xem trước phải thành công"
        );

        assertNotNull(result.getModelAndView());

        Object token = result.getModelAndView()
                .getModel()
                .get("checkoutToken");

        assertInstanceOf(
                String.class,
                token,
                "Buyer phải nhận được checkoutToken"
        );

        return (String) token;
    }

    /*
     * Xác nhận checkout bằng API thật.
     */
    private MvcResult confirm(
            Buyer buyer,
            String token) throws Exception {

        return mvc.perform(
                post("/checkout/confirm")
                        .session(buyer.session())
                        .with(user(buyer.email()).roles("USER"))
                        .with(csrf())
                        .param("checkoutToken", token)
        ).andReturn();
    }

    private int countOrders(Long buyerId) {
        return db.queryForObject(
                """
                SELECT COUNT(*)
                FROM orders
                WHERE buyer_id = ?
                """,
                Integer.class,
                buyerId
        );
    }

    private int countOrderItems(Long buyerId) {
        return db.queryForObject(
                """
                SELECT COUNT(*)
                FROM order_items i
                JOIN orders o ON o.id = i.order_id
                WHERE o.buyer_id = ?
                """,
                Integer.class,
                buyerId
        );
    }

    private int countPayments(Long buyerId) {
        return db.queryForObject(
                """
                SELECT COUNT(*)
                FROM payments p
                JOIN orders o ON o.id = p.order_id
                WHERE o.buyer_id = ?
                """,
                Integer.class,
                buyerId
        );
    }

    private int countCartItems(Long cartId) {
        return db.queryForObject(
                """
                SELECT COUNT(*)
                FROM cart_items
                WHERE cart_id = ?
                """,
                Integer.class,
                cartId
        );
    }

    /*
     * Dọn dữ liệu test để không ảnh hưởng test khác.
     */
    private void cleanupBuyer(Buyer buyer) {

        db.update(
                """
                DELETE FROM order_status_histories
                WHERE order_id IN (
                    SELECT id FROM orders WHERE buyer_id = ?
                )
                """,
                buyer.userId()
        );

        db.update(
                """
                DELETE FROM payments
                WHERE order_id IN (
                    SELECT id FROM orders WHERE buyer_id = ?
                )
                """,
                buyer.userId()
        );

        db.update(
                """
                DELETE FROM order_items
                WHERE order_id IN (
                    SELECT id FROM orders WHERE buyer_id = ?
                )
                """,
                buyer.userId()
        );

        db.update(
                "DELETE FROM orders WHERE buyer_id = ?",
                buyer.userId()
        );

        db.update(
                "DELETE FROM cart_items WHERE cart_id = ?",
                buyer.cartId()
        );

        db.update(
                "DELETE FROM carts WHERE id = ?",
                buyer.cartId()
        );

        db.update(
                "DELETE FROM users WHERE id = ?",
                buyer.userId()
        );
    }

    @Test
    void twoBuyersCompetingForLastItemOnlyOneCanCheckout()
            throws Exception {

        Long productId = productId("Canvas Tote Bag");

        int originalStock = stock(productId);

        List<Buyer> createdBuyers = new ArrayList<>();

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            // =====================================
            // BƯỚC 1: Chỉ còn 1 sản phẩm trong kho
            // =====================================

            db.update(
                    """
                    UPDATE products
                    SET stock_quantity = 1
                    WHERE id = ?
                    """,
                    productId
            );

            assertEquals(1, stock(productId));

            // =====================================
            // BƯỚC 2: Tạo hai buyer độc lập
            // =====================================

            Buyer buyerA = createBuyer(productId);
            createdBuyers.add(buyerA);

            Buyer buyerB = createBuyer(productId);
            createdBuyers.add(buyerB);

            assertNotEquals(
                    buyerA.userId(),
                    buyerB.userId()
            );

            assertNotEquals(
                    buyerA.cartId(),
                    buyerB.cartId()
            );

            // =====================================
            // BƯỚC 3: Cả hai cùng xem trước
            // =====================================

            String tokenA = preview(buyerA);
            String tokenB = preview(buyerB);

            assertNotEquals(tokenA, tokenB);

            // Cả hai chưa được tạo đơn.
            assertEquals(0, countOrders(buyerA.userId()));
            assertEquals(0, countOrders(buyerB.userId()));

            // Tồn kho vẫn là 1.
            assertEquals(1, stock(productId));

            // =====================================
            // BƯỚC 4: Chuẩn bị xác nhận đồng thời
            // =====================================

            CountDownLatch ready =
                    new CountDownLatch(2);

            CountDownLatch start =
                    new CountDownLatch(1);

            // Bật cổng đồng bộ trước khi trừ kho.
            gate.enabled.set(true);

            Future<MvcResult> futureA =
                    executor.submit(() -> {

                        ready.countDown();

                        if (!start.await(
                                15,
                                TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Buyer A không nhận được tín hiệu bắt đầu"
                            );
                        }

                        return confirm(buyerA, tokenA);
                    });

            Future<MvcResult> futureB =
                    executor.submit(() -> {

                        ready.countDown();

                        if (!start.await(
                                15,
                                TimeUnit.SECONDS)) {
                            throw new IllegalStateException(
                                    "Buyer B không nhận được tín hiệu bắt đầu"
                            );
                        }

                        return confirm(buyerB, tokenB);
                    });

            // Đảm bảo hai luồng đã sẵn sàng.
            assertTrue(
                    ready.await(15, TimeUnit.SECONDS),
                    "Hai request phải sẵn sàng"
            );

            // =====================================
            // BƯỚC 5: Phát lệnh chạy cùng lúc
            // =====================================

            start.countDown();

            MvcResult resultA =
                    futureA.get(30, TimeUnit.SECONDS);

            MvcResult resultB =
                    futureB.get(30, TimeUnit.SECONDS);

            int statusA = resultA.getResponse().getStatus();
            int statusB = resultB.getResponse().getStatus();

            // =====================================
            // BƯỚC 6: Xác minh tranh chấp thật
            // =====================================

            assertEquals(
                    2,
                    gate.arrivals.get(),
                    "Cả hai checkout phải đến bước trừ kho"
            );

            // Đếm số lần thành công.
            int successCount = 0;

            if (statusA == 302) {
                successCount++;
            }

            if (statusB == 302) {
                successCount++;
            }

            assertEquals(
                    1,
                    successCount,
                    "Chỉ một trong hai buyer được đặt hàng"
            );

            // Một thành công, một bị từ chối.
            assertTrue(
                    (statusA == 302 && statusB == 200)
                    ||
                    (statusA == 200 && statusB == 302),
                    "Kết quả phải gồm một redirect thành công và một màn hình lỗi"
            );

            Buyer winner =
                    statusA == 302 ? buyerA : buyerB;

            Buyer loser =
                    statusA == 302 ? buyerB : buyerA;

            MvcResult winnerResult =
                    statusA == 302 ? resultA : resultB;

            MvcResult loserResult =
                    statusA == 302 ? resultB : resultA;

            // =====================================
            // BƯỚC 7: Kiểm tra buyer thành công
            // =====================================

            assertEquals(
                    "/checkout/success",
                    winnerResult.getResponse().getRedirectedUrl()
            );

            assertEquals(
                    1,
                    countOrders(winner.userId()),
                    "Buyer thành công phải có đúng một đơn"
            );

            assertEquals(
                    1,
                    countOrderItems(winner.userId())
            );

            assertEquals(
                    1,
                    countPayments(winner.userId())
            );

            assertEquals(
                    0,
                    countCartItems(winner.cartId()),
                    "Giỏ của buyer thành công phải được xóa mục đã mua"
            );

            // =====================================
            // BƯỚC 8: Kiểm tra buyer thất bại
            // =====================================

            assertNotNull(loserResult.getModelAndView());

            assertEquals(
                    "ordering/checkout/index",
                    loserResult.getModelAndView().getViewName()
            );

            assertNotNull(
                    loserResult.getModelAndView()
                            .getModel()
                            .get("checkoutError"),
                    "Buyer thất bại phải thấy thông báo lỗi"
            );

            assertEquals(
                    0,
                    countOrders(loser.userId()),
                    "Buyer thất bại không được tạo order"
            );

            assertEquals(
                    0,
                    countOrderItems(loser.userId()),
                    "Buyer thất bại không được có order item"
            );

            assertEquals(
                    0,
                    countPayments(loser.userId()),
                    "Buyer thất bại không được tạo payment"
            );

            assertEquals(
                    1,
                    countCartItems(loser.cartId()),
                    "Buyer thất bại phải giữ nguyên giỏ hàng"
            );

            // =====================================
            // BƯỚC 9: Kiểm tra tồn kho cuối cùng
            // =====================================

            assertEquals(
                    0,
                    stock(productId),
                    "Tồn kho cuối cùng phải bằng 0"
            );

            assertEquals(
                    1,
                    countOrders(buyerA.userId())
                            + countOrders(buyerB.userId())
            );

            assertEquals(
                    1,
                    countPayments(buyerA.userId())
                            + countPayments(buyerB.userId())
            );

        } finally {

            gate.enabled.set(false);

            executor.shutdownNow();

            if (!executor.awaitTermination(
                    15,
                    TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Không thể kết thúc các luồng kiểm thử"
                );
            }

            // Xóa dữ liệu riêng của hai buyer.
            for (Buyer buyer : createdBuyers) {
                cleanupBuyer(buyer);
            }

            // Khôi phục số lượng tồn kho ban đầu.
            db.update(
                    """
                    UPDATE products
                    SET stock_quantity = ?
                    WHERE id = ?
                    """,
                    originalStock,
                    productId
            );
        }
    }
}
