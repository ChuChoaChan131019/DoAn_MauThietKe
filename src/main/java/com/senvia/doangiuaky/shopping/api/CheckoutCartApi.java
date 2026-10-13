package com.senvia.doangiuaky.shopping.api;

import java.util.List;

/**
 * Cart operations used by Ordering inside one checkout transaction.
 *
 * <p>The buyer identifier must come from the authenticated principal, never from a client-controlled
 * request field. Implementations validate that the buyer is an active user and scope every cart
 * operation to that buyer.</p>
 */
public interface CheckoutCartApi {

    /**
     * Returns every item in the buyer's cart while holding the cart lock until transaction end.
     * Price, stock and saleability are deliberately excluded and must be revalidated by Ordering.
     */
    List<CheckoutCartItem> getCheckoutSnapshot(Long buyerId);

    /**
     * Removes only matching owned items. Invalid, duplicate, foreign or stale items fail the checkout
     * transaction; replaying a committed cleanup is therefore a safe failure rather than a successful
     * idempotent response.
     */
    void removePurchasedItems(Long buyerId, List<CheckoutCartItem> purchasedItems);
}
