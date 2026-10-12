package com.senvia.doangiuaky.merchant.api;

/**
 * Public order capabilities of a shop.
 *
 * <p>The two capabilities are deliberately independent: a locked shop cannot
 * receive new orders, but may still complete orders created before the lock.</p>
 */
public record ShopOrderCapabilities(
        Long shopId,
        boolean canReceiveNewOrders,
        boolean canHandleExistingOrders) {
}
