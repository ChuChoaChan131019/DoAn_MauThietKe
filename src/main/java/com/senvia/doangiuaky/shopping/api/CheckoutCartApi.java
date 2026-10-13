package com.senvia.doangiuaky.shopping.api;

import java.util.List;

/**
 * Cart operations used by Ordering inside one checkout transaction.
 *
 * <p>The implementation resolves the buyer from the authenticated principal. The contract deliberately
 * exposes no buyer identifier that a client or consumer could substitute.</p>
 */
public interface CheckoutCartApi {

    /**
     * Returns every item in the buyer's cart while holding the cart lock until transaction end.
     * Price, stock and saleability are deliberately excluded and must be revalidated by Ordering.
     */
    List<CheckoutCartItem> getCheckoutSnapshot();

    /**
     * Removes only matching owned items. Invalid, duplicate, foreign or stale items fail the checkout
     * transaction; replaying a committed cleanup is therefore a safe failure rather than a successful
     * idempotent response.
     */
    void removePurchasedItems(List<CheckoutCartItem> purchasedItems);
}
