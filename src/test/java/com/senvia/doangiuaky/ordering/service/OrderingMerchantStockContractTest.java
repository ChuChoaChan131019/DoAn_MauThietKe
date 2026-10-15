package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantStockApi;
import com.senvia.doangiuaky.merchant.api.MerchantStockException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Ordering uses only Merchant's public read and stock contracts. */
@SpringBootTest
class OrderingMerchantStockContractTest {

    @Autowired private MerchantApi merchant;
    @Autowired private MerchantStockApi stock;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedCheckoutRollsBackAllPriorMerchantDeductions() {
        Long productId = canvasProductId();
        CartProductView before = merchant.findProductForCart(productId).orElseThrow();

        assertThrows(MerchantStockException.class, () -> transactionTemplate.execute(status -> {
            stock.deductForCheckout(before.shopId(), productId, 1, before.currentPrice());
            stock.deductForCheckout(before.shopId(), productId,
                    before.stockQuantity() + 1, before.currentPrice());
            return null;
        }));

        assertEquals(before.stockQuantity(),
                merchant.findProductForCart(productId).orElseThrow().stockQuantity());
    }

    @Test
    @Transactional
    void cancelledOrderItemRestoresStockOnlyOnceForOrderingRetry() {
        Long productId = canvasProductId();
        CartProductView before = merchant.findProductForCart(productId).orElseThrow();
        long orderItemId = Long.MAX_VALUE - 500;

        stock.restoreCancelledOrderItem(orderItemId, before.shopId(), productId, 1);
        stock.restoreCancelledOrderItem(orderItemId, before.shopId(), productId, 1);

        assertEquals(before.stockQuantity() + 1,
                merchant.findProductForCart(productId).orElseThrow().stockQuantity());
    }

    private Long canvasProductId() {
        return jdbc.queryForObject("select id from products where name = 'Canvas Tote Bag'", Long.class);
    }
}
