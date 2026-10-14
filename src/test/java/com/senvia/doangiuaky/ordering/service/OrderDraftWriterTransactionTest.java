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

        List<Long> shops = jdbc.queryForList(
                "SELECT id FROM shops ORDER BY id LIMIT 2",
                Long.class
        );

        assertNotNull(buyer);
        assertEquals(
                2,
                shops.size(),
                "Demo seed must contain at least two shops"
        );

        Long productA = jdbc.queryForObject(
                "SELECT MIN(id) FROM products WHERE shop_id = ?",
                Long.class,
                shops.get(0)
        );

        Long productB = jdbc.queryForObject(
                "SELECT MIN(id) FROM products WHERE shop_id = ?",
                Long.class,
                shops.get(1)
        );

        assertNotNull(productA);
        assertNotNull(productB);

        var one = new PreparedCheckout.ShopOrder(
                buyer,
                shops.get(0),
                List.of(
                        new PreparedCheckout.Line(
                                productA,
                                "Snapshot A",
                                new BigDecimal("100.00"),
                                2,
                                new BigDecimal("200.00")
                        )
                ),
                new BigDecimal("200.00")
        );

        var two = new PreparedCheckout.ShopOrder(
                buyer,
                shops.get(1),
                List.of(
                        new PreparedCheckout.Line(
                                productB,
                                "Snapshot B",
                                new BigDecimal("300.00"),
                                1,
                                new BigDecimal("300.00")
                        )
                ),
                new BigDecimal("300.00")
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
                new BigDecimal("500.00"),
                List.of()
        );
    }
}