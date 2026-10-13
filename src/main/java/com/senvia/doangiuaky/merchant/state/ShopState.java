package com.senvia.doangiuaky.merchant.state;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;

/** Capabilities granted to a shop at a particular point in its lifecycle. */
public interface ShopState {

    ShopStatus status();

    boolean canAddProduct();

    boolean canReceiveOrder();

    boolean canResubmit();

    boolean canHandleExistingOrders();

    default ShopStatus approve() {
        throw new InvalidShopStateTransitionException(status(), ShopStatus.APPROVED);
    }

    default ShopStatus reject() {
        throw new InvalidShopStateTransitionException(status(), ShopStatus.REJECTED);
    }

    default ShopStatus resubmit() {
        throw new InvalidShopStateTransitionException(status(), ShopStatus.PENDING);
    }

    default ShopStatus lock() {
        throw new InvalidShopStateTransitionException(status(), ShopStatus.LOCKED);
    }

    default ShopStatus unlock() {
        throw new InvalidShopStateTransitionException(status(), ShopStatus.APPROVED);
    }
}
