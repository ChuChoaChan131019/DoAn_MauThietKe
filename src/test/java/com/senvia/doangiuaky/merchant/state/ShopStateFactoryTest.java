package com.senvia.doangiuaky.merchant.state;

import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShopStateFactoryTest {

    @Test
    void resolvesLifecycleCapabilitiesForEveryShopStatus() {
        assertState(ShopStatus.PENDING, PendingShopState.class, false, false, false, false);
        assertState(ShopStatus.APPROVED, ApprovedShopState.class, true, true, false, true);
        assertState(ShopStatus.REJECTED, RejectedShopState.class, false, false, true, false);
        assertState(ShopStatus.LOCKED, LockedShopState.class, false, false, false, true);
    }

    @Test
    void rejectsNullStatus() {
        assertThrows(NullPointerException.class, () -> ShopStateFactory.resolve(null));
    }

    @Test
    void onlyPendingStateCanApprove() {
        assertEquals(ShopStatus.APPROVED, ShopStateFactory.resolve(ShopStatus.PENDING).approve());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.APPROVED).approve());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.REJECTED).approve());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.LOCKED).approve());
    }

    @Test
    void onlyPendingStateCanReject() {
        assertEquals(ShopStatus.REJECTED, ShopStateFactory.resolve(ShopStatus.PENDING).reject());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.APPROVED).reject());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.REJECTED).reject());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.LOCKED).reject());
    }

    @Test
    void onlyRejectedStateCanResubmit() {
        assertEquals(ShopStatus.PENDING, ShopStateFactory.resolve(ShopStatus.REJECTED).resubmit());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.PENDING).resubmit());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.APPROVED).resubmit());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.LOCKED).resubmit());
    }

    @Test
    void onlyApprovedStateCanLock() {
        assertEquals(ShopStatus.LOCKED, ShopStateFactory.resolve(ShopStatus.APPROVED).lock());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.PENDING).lock());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.REJECTED).lock());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.LOCKED).lock());
    }

    @Test
    void onlyLockedStateCanUnlock() {
        assertEquals(ShopStatus.APPROVED, ShopStateFactory.resolve(ShopStatus.LOCKED).unlock());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.PENDING).unlock());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.APPROVED).unlock());
        assertThrows(InvalidShopStateTransitionException.class,
                () -> ShopStateFactory.resolve(ShopStatus.REJECTED).unlock());
    }

    private void assertState(ShopStatus status, Class<? extends ShopState> stateType,
                             boolean canAddProduct, boolean canReceiveOrder,
                             boolean canResubmit, boolean canHandleExistingOrders) {
        ShopState state = ShopStateFactory.resolve(status);

        assertInstanceOf(stateType, state);
        assertEquals(status, state.status());
        assertEquals(canAddProduct, state.canAddProduct());
        assertEquals(canReceiveOrder, state.canReceiveOrder());
        assertEquals(canResubmit, state.canResubmit());
        assertEquals(canHandleExistingOrders, state.canHandleExistingOrders());
    }
}
