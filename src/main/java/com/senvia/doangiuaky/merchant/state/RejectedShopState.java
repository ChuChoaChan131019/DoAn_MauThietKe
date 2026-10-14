package com.senvia.doangiuaky.merchant.state;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;

public final class RejectedShopState implements ShopState {

    @Override public ShopStatus status() { return ShopStatus.REJECTED; }
    @Override public boolean canAddProduct() { return false; }
    @Override public boolean canReceiveOrder() { return false; }
    @Override public boolean canBePubliclyListed() { return false; }
    @Override public boolean canResubmit() { return true; }
    @Override public boolean canHandleExistingOrders() { return false; }
    @Override public ShopStatus resubmit() { return ShopStatus.PENDING; }
}
