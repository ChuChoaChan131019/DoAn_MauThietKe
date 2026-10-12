package com.senvia.doangiuaky.merchant.state;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;

public final class LockedShopState implements ShopState {

    @Override public ShopStatus status() { return ShopStatus.LOCKED; }
    @Override public boolean canAddProduct() { return false; }
    @Override public boolean canReceiveOrder() { return false; }
    @Override public boolean canResubmit() { return false; }
    @Override public boolean canHandleExistingOrders() { return true; }
    @Override public ShopStatus unlock() { return ShopStatus.APPROVED; }
}
