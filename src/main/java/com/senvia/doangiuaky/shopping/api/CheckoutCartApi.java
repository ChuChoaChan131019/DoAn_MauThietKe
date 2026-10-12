package com.senvia.doangiuaky.shopping.api;

import java.util.List;

/** Cart operations used by Ordering inside one checkout transaction. */
public interface CheckoutCartApi {

    /** Returns every item in the buyer's cart while holding the cart lock until transaction end. */
    List<CheckoutCartItem> getCheckoutSnapshot(Long buyerId);

    /** Removes only matching owned items; invalid or stale items fail the checkout transaction. */
    void removePurchasedItems(Long buyerId, List<CheckoutCartItem> purchasedItems);
}
