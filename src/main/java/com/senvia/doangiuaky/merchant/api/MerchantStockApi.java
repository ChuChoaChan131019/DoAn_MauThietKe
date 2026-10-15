package com.senvia.doangiuaky.merchant.api;

import java.math.BigDecimal;

/** Merchant stock commands used inside the caller's checkout/cancellation transaction. */
public interface MerchantStockApi {

    /**
     * Deducts one product only if it is saleable, has the expected current price and enough stock.
     * Callers processing multiple products must use ascending product IDs to avoid lock cycles.
     * Any failure throws an unchecked {@link MerchantStockException} and rolls back the caller.
     */
    void deductForCheckout(Long shopId, Long productId, int quantity, BigDecimal expectedUnitPrice);

    /**
     * Restores one cancelled order item once. The order-item ID is an opaque idempotency key;
     * Merchant does not read Ordering entities. Repeating the same payload is a no-op.
     */
    void restoreCancelledOrderItem(Long orderItemId, Long shopId, Long productId, int quantity);
}
