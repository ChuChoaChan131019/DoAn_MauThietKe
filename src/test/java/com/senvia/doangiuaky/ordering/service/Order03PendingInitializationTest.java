
package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class Order03PendingInitializationTest {

    @Autowired
    OrderDraftWriter writer;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    private record ProductRow(
            Long productId,
            Long shopId,
            BigDecimal price
    ) {
    }

    @Test
    void newOrdersMustStartPendingWithOneInitialHistory() {
        long historiesBefore = count("order_status_histories");

        PreparedCheckout checkout = sampleCheckout();

        transactions.executeWithoutResult(transaction -> {

            List<Long> orderIds = writer.save(
                    checkout,
                    (method, code, amount) ->
                            new OrderDraftWriter.PaymentSeed(
                                    PaymentStatus.COD_PENDING,
                                    null
                            )
            );

            // Hai shop phai tao hai order.
            assertEquals(2, orderIds.size());

            for (Long orderId : orderIds) {

                String status = jdbc.queryForObject(
                        "SELECT status FROM orders WHERE id = ?",
                        String.class,
                        orderId
                );

                assertEquals("PENDING", status);

                // Moi order co dung mot lich su khoi tao.
                Integer initialHistoryCount =
                        jdbc.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM order_status_histories
                                WHERE order_id = ?
                                  AND old_status IS NULL
                                  AND new_status = 'PENDING'
                                  AND changed_by = ?
                                """,
                                Integer.class,
                                orderId,
                                checkout.buyerId()
                        );

                assertEquals(1, initialHistoryCount);

                // Khong co lich su thua.
                Integer totalHistoryCount =
                        jdbc.queryForObject(
                                """
                                SELECT COUNT(*)
                                FROM order_status_histories
                                WHERE order_id = ?
                                """,
                                Integer.class,
                                orderId
                        );

                assertEquals(1, totalHistoryCount);

                // Moi order phai co payment.
                Integer paymentCount = jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM payments
                        WHERE order_id = ?
                        """,
                        Integer.class,
                        orderId
                );

                assertEquals(1, paymentCount);

                // Moi order phai co it nhat mot san pham.
                Integer itemCount = jdbc.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM order_items
                        WHERE order_id = ?
                        """,
                        Integer.class,
                        orderId
                );

                assertTrue(itemCount > 0);

                // Tong tien cua order phai duong.
                BigDecimal total = jdbc.queryForObject(
                        """
                        SELECT total_amount
                        FROM orders
                        WHERE id = ?
                        """,
                        BigDecimal.class,
                        orderId
                );

                assertNotNull(total);
                assertTrue(total.signum() > 0);
            }

            assertEquals(
                    historiesBefore + 2,
                    count("order_status_histories")
            );

            // Khong de test ghi du lieu vao DB.
            transaction.setRollbackOnly();
        });

        assertEquals(
                historiesBefore,
                count("order_status_histories")
        );
    }

    @Test
    void secondShopPaymentFailureRollsBackInitialHistories() {
        long ordersBefore = count("orders");
        long itemsBefore = count("order_items");
        long paymentsBefore = count("payments");
        long historiesBefore = count("order_status_histories");

        PreparedCheckout checkout = sampleCheckout();

        AtomicInteger attempts = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> transactions.executeWithoutResult(
                        transaction -> writer.save(
                                checkout,
                                (method, code, amount) -> {

                                    if (attempts.incrementAndGet() == 2) {
                                        throw new IllegalStateException(
                                                "Simulated second shop failure"
                                        );
                                    }

                                    return new OrderDraftWriter.PaymentSeed(
                                            PaymentStatus.COD_PENDING,
                                            null
                                    );
                                }
                        )
                )
        );

        assertEquals(2, attempts.get());

        assertEquals(ordersBefore, count("orders"));
        assertEquals(itemsBefore, count("order_items"));
        assertEquals(paymentsBefore, count("payments"));
        assertEquals(historiesBefore, count("order_status_histories"));
    }

    private long count(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Long.class
        );
    }

    private PreparedCheckout sampleCheckout() {
        Long buyerId = jdbc.queryForObject(
                "SELECT MIN(id) FROM users",
                Long.class
        );

        assertNotNull(buyerId);

        List<ProductRow> products = jdbc.query(
                """
                SELECT product_id, shop_id, price
                FROM (
                    SELECT
                        p.id AS product_id,
                        p.shop_id,
                        p.price,
                        ROW_NUMBER() OVER (
                            PARTITION BY p.shop_id
                            ORDER BY p.id
                        ) AS shop_row
                    FROM products p
                    JOIN shops s ON s.id = p.shop_id
                    JOIN categories c ON c.id = p.category_id
                    JOIN users u ON u.id = s.owner_id
                    WHERE p.status = 'ACTIVE'
                      AND p.stock_quantity >= 2
                      AND s.status = 'APPROVED'
                      AND c.active = TRUE
                      AND u.account_status = 'ACTIVE'
                ) available_products
                WHERE shop_row = 1
                ORDER BY shop_id, product_id
                LIMIT 2
                """,
                (rs, rowNum) -> new ProductRow(
                        rs.getLong("product_id"),
                        rs.getLong("shop_id"),
                        rs.getBigDecimal("price")
                )
        );

        assertEquals(
                2,
                products.size(),
                "Cần có sản phẩm hợp lệ từ hai shop."
        );

        List<PreparedCheckout.ShopOrder> groups =
                new ArrayList<>();

        BigDecimal grandTotal = BigDecimal.ZERO;

        for (ProductRow product : products) {
            BigDecimal price = product.price();

            var line = new PreparedCheckout.Line(
                    product.productId(),
                    "ORDER 03 Test Product",
                    price,
                    1,
                    price
            );

            groups.add(
                    new PreparedCheckout.ShopOrder(
                            buyerId,
                            product.shopId(),
                            List.of(line),
                            price
                    )
            );

            grandTotal = grandTotal.add(price);
        }

        return new PreparedCheckout(
                buyerId,
                new PreparedCheckout.DeliverySnapshot(
                        "ORDER 03 Test Buyer",
                        "0901234567",
                        "Da Lat",
                        null
                ),
                PaymentMethod.COD,
                groups,
                grandTotal,
                List.of()
        );
    }
}