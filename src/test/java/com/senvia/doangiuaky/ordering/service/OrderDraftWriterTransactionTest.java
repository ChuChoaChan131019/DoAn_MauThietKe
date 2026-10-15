package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderDraftWriterTransactionTest {
    @Autowired
    OrderDraftWriter writer;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void rejectsProductAssignedToAnotherShopBeforeWritingOrders() {

        long ordersBefore = count("orders");
        long linesBefore = count("order_items");
        long paymentsBefore = count("payments");

        PreparedCheckout valid = fixture();
        var first = valid.shopOrders().get(0);
        var second = valid.shopOrders().get(1);
        var originalLine = second.items().get(0);
        var misplacedLine = new PreparedCheckout.Line(
                first.items().get(0).productId(),
                originalLine.productNameSnapshot(),
                originalLine.unitPrice(),
                originalLine.quantity(),
                originalLine.subtotal()
        );
        var wrongGroup = new PreparedCheckout.ShopOrder(
                second.buyerId(),
                second.shopId(),
                List.of(misplacedLine),
                second.totalAmount()
        );
        var forged = new PreparedCheckout(
                valid.buyerId(),
                valid.delivery(),
                valid.paymentMethod(),
                List.of(first, wrongGroup),
                valid.grandTotal(),
                valid.purchasedCartItems()
        );

        CheckoutPreparationException error = assertThrows(
                CheckoutPreparationException.class,
                () -> transactions.executeWithoutResult(status -> writer.save(
                        forged,
                        (method, code, total) -> {
                            fail("Không được khởi tạo payment cho nhóm sai shop");
                            return null;
                        }
                ))
        );

        assertTrue(error.getMessage().contains("không thuộc gian hàng"));
        assertEquals(ordersBefore, count("orders"));
        assertEquals(linesBefore, count("order_items"));
        assertEquals(paymentsBefore, count("payments"));
    }

    @Test
    void savesOneOrderPaymentAndSnapshotPerShopInCallerTransaction() {

        long before = count("orders");
        long paymentsBefore = count("payments");
        long linesBefore = count("order_items");

        PreparedCheckout data = fixture();

        transactions.executeWithoutResult(status -> {

            List<Long> ids = writer.save(
                    data,
                    (method, code, total) ->
                            new OrderDraftWriter.PaymentSeed(
                                    PaymentStatus.COD_PENDING,
                                    null
                            )
            );

            assertEquals(2, ids.size());

            assertEquals(
                    2L,
                    count("orders") - before
            );

            assertEquals(
                    2L,
                    count("payments") - paymentsBefore
            );

            assertEquals(
                    2L,
                    count("order_items") - linesBefore
            );

            status.setRollbackOnly();
        });

        assertEquals(before, count("orders"));
        assertEquals(paymentsBefore, count("payments"));
        assertEquals(linesBefore, count("order_items"));
    }

    @Test
    void secondShopPaymentFailureRollsBackEveryOrderItemAndPayment() {

        long ordersBefore = count("orders");
        long linesBefore = count("order_items");
        long paymentsBefore = count("payments");

        AtomicInteger visits = new AtomicInteger();

        assertThrows(
                IllegalStateException.class,
                () -> transactions.executeWithoutResult(
                        status -> writer.save(
                                fixture(),
                                (method, code, total) -> {

                                    if (visits.incrementAndGet() == 2) {
                                        throw new IllegalStateException(
                                                "second shop payment failed"
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

        assertEquals(ordersBefore, count("orders"));
        assertEquals(linesBefore, count("order_items"));
        assertEquals(paymentsBefore, count("payments"));
    }

    @Test
    void changedPriceStopsAllShopsBeforeAnyOrderSideWrite() {
        long ordersBefore = count("orders");
        long linesBefore = count("order_items");
        long paymentsBefore = count("payments");
        PreparedCheckout checkout = fixture();
        Long changedProductId = checkout.shopOrders().get(1).items().getFirst().productId();
        Integer stockBefore = jdbc.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, changedProductId);

        CheckoutPriceChangedException error = assertThrows(
                CheckoutPriceChangedException.class,
                () -> transactions.executeWithoutResult(status -> {
                    jdbc.update("UPDATE products SET price = price + 1 WHERE id = ?", changedProductId);
                    writer.save(
                            checkout,
                            (method, code, total) -> {
                                fail("Không được khởi tạo payment khi giá đã đổi");
                                return null;
                            });
                }));

        assertTrue(error.getCurrentGrandTotal().compareTo(checkout.grandTotal()) > 0);
        assertEquals(ordersBefore, count("orders"));
        assertEquals(linesBefore, count("order_items"));
        assertEquals(paymentsBefore, count("payments"));
        assertEquals(stockBefore, jdbc.queryForObject(
                "SELECT stock_quantity FROM products WHERE id = ?", Integer.class, changedProductId));
    }

    private long count(String table) {

        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Long.class
        );
    }

    private PreparedCheckout fixture() {

        Long buyer = jdbc.queryForObject(
                "SELECT MIN(id) FROM users",
                Long.class
        );

        List<java.util.Map<String, Object>> products = jdbc.queryForList("""
                SELECT product_id, shop_id, price
                FROM (
                    SELECT p.id AS product_id, p.shop_id, p.price,
                           ROW_NUMBER() OVER (PARTITION BY p.shop_id ORDER BY p.id) AS shop_row
                    FROM products p
                    JOIN shops s ON s.id = p.shop_id
                    JOIN categories c ON c.id = p.category_id
                    JOIN users u ON u.id = s.owner_id
                    WHERE p.status = 'ACTIVE'
                      AND p.stock_quantity >= 2
                      AND s.status = 'APPROVED'
                      AND c.active = TRUE
                      AND u.account_status = 'ACTIVE'
                ) saleable_products
                WHERE shop_row = 1
                ORDER BY shop_id, product_id
                LIMIT 2
                """);

        assertNotNull(buyer);
        assertEquals(
                2,
                products.size(),
                "Demo seed must contain at least two saleable products"
        );

        Long productA = ((Number) products.get(0).get("PRODUCT_ID")).longValue();
        Long shopA = ((Number) products.get(0).get("SHOP_ID")).longValue();
        BigDecimal priceA = (BigDecimal) products.get(0).get("PRICE");
        Long productB = ((Number) products.get(1).get("PRODUCT_ID")).longValue();
        Long shopB = ((Number) products.get(1).get("SHOP_ID")).longValue();
        BigDecimal priceB = (BigDecimal) products.get(1).get("PRICE");

        var one = new PreparedCheckout.ShopOrder(
                buyer,
                shopA,
                List.of(
                        new PreparedCheckout.Line(
                                productA,
                                "Snapshot A",
                                priceA,
                                2,
                                priceA.multiply(new BigDecimal("2"))
                        )
                ),
                priceA.multiply(new BigDecimal("2"))
        );

        var two = new PreparedCheckout.ShopOrder(
                buyer,
                shopB,
                List.of(
                        new PreparedCheckout.Line(
                                productB,
                                "Snapshot B",
                                priceB,
                                1,
                                priceB
                        )
                ),
                priceB
        );

        return new PreparedCheckout(
                buyer,
                new PreparedCheckout.DeliverySnapshot(
                        "Recipient",
                        "0901234567",
                        "Đà Lạt",
                        null
                ),
                PaymentMethod.COD,
                List.of(one, two),
                one.totalAmount().add(two.totalAmount()),
                List.of()
        );
    }
}
