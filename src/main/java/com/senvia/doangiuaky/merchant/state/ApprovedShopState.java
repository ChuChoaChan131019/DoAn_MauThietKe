package com.senvia.doangiuaky.merchant.state;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;

public final class ApprovedShopState implements ShopState {

    @Override public ShopStatus status() { return ShopStatus.APPROVED; }
    @Override public boolean canAddProduct() { return true; }
    @Override public boolean canReceiveOrder() { return true; }
    @Override public boolean canResubmit() { return false; }
    @Override public boolean canHandleExistingOrders() { return true; }
    @Override public ShopStatus lock() { return ShopStatus.LOCKED; }
}
