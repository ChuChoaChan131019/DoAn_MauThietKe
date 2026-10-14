package com.senvia.doangiuaky.merchant.state;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;

public final class PendingShopState implements ShopState {

    @Override public ShopStatus status() { return ShopStatus.PENDING; }
    @Override public boolean canAddProduct() { return false; }
    @Override public boolean canReceiveOrder() { return false; }
    @Override public boolean canBePubliclyListed() { return false; }
    @Override public boolean canResubmit() { return false; }
    @Override public boolean canHandleExistingOrders() { return false; }
    @Override public ShopStatus approve() { return ShopStatus.APPROVED; }
    @Override public ShopStatus reject() { return ShopStatus.REJECTED; }
}
