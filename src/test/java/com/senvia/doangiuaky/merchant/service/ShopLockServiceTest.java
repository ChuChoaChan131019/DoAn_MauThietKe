package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.state.InvalidShopStateTransitionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopLockServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private IdentityApi identityApi;

    private final UserSummary admin = new UserSummary(
            99L, "Admin", UserRole.ADMIN, AccountStatus.ACTIVE);

    @Test
    void locksApprovedShopAndStoresNormalizedAuditMetadata() {
        Shop shop = approvedShop();
        when(identityApi.findUser(99L)).thenReturn(Optional.of(admin));
        when(shopRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(shop));

        new ShopLockService(shopRepository, identityApi).lock(10L, 99L, "  Vi phạm chính sách  ");

        assertEquals(ShopStatus.LOCKED, shop.getStatus());
        assertEquals("Vi phạm chính sách", shop.getLockReason());
        assertEquals(99L, shop.getLockedById());
        assertNotNull(shop.getLockedAt());
        assertEquals(7L, shop.getApprovedById());
        assertNotNull(shop.getApprovedAt());
        verify(shopRepository).saveAndFlush(shop);
    }

    @Test
    void unlocksLockedShopAndClearsOnlyLockMetadata() {
        Shop shop = approvedShop();
        shop.lock(99L, "Vi phạm chính sách", Instant.parse("2026-10-11T10:00:00Z"));
        when(identityApi.findUser(99L)).thenReturn(Optional.of(admin));
        when(shopRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(shop));

        new ShopLockService(shopRepository, identityApi).unlock(10L, 99L);

        assertEquals(ShopStatus.APPROVED, shop.getStatus());
        assertNull(shop.getLockReason());
        assertNull(shop.getLockedById());
        assertNull(shop.getLockedAt());
        assertEquals(7L, shop.getApprovedById());
        assertNotNull(shop.getApprovedAt());
        verify(shopRepository).saveAndFlush(shop);
    }

    @Test
    void rejectsWrongStateWithoutSaving() {
        Shop pendingShop = Shop.createPending(
                3L, "Pending", null, null, null, "0900000000", "Address");
        when(identityApi.findUser(99L)).thenReturn(Optional.of(admin));
        when(shopRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(pendingShop));

        assertThrows(InvalidShopStateTransitionException.class,
                () -> new ShopLockService(shopRepository, identityApi)
                        .lock(10L, 99L, "Vi phạm chính sách"));

        verify(shopRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsNonAdminBeforeLoadingShop() {
        when(identityApi.findUser(99L)).thenReturn(Optional.of(new UserSummary(
                99L, "User", UserRole.USER, AccountStatus.ACTIVE)));

        assertThrows(ShopLockAuthorizationException.class,
                () -> new ShopLockService(shopRepository, identityApi)
                        .lock(10L, 99L, "Vi phạm chính sách"));

        verify(shopRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsBlankOrOverlongReasonBeforeLoadingActorAndShop() {
        ShopLockService service = new ShopLockService(shopRepository, identityApi);

        assertThrows(IllegalArgumentException.class, () -> service.lock(10L, 99L, "   "));
        assertThrows(IllegalArgumentException.class, () -> service.lock(10L, 99L, "a".repeat(501)));

        verify(identityApi, never()).findUser(any());
        verify(shopRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void rejectsMissingShop() {
        when(identityApi.findUser(99L)).thenReturn(Optional.of(admin));
        when(shopRepository.findByIdForUpdate(10L)).thenReturn(Optional.empty());

        assertThrows(ShopManagementNotFoundException.class,
                () -> new ShopLockService(shopRepository, identityApi)
                        .unlock(10L, 99L));

        verify(shopRepository, never()).saveAndFlush(any());
    }

    private static Shop approvedShop() {
        Shop shop = Shop.createPending(
                3L, "Approved", null, null, null, "0900000000", "Address");
        shop.approve(7L, Instant.parse("2026-10-10T10:00:00Z"));
        return shop;
    }
}
