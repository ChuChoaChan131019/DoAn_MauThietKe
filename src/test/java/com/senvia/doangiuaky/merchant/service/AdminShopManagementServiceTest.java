package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminShopManagementServiceTest {

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private IdentityApi identityApi;

    @Test
    void listsOnlyManagedShopsWithOwnerName() {
        Shop shop = mockShop(10L, ShopStatus.APPROVED);
        allowAdmin();
        when(shopRepository.findAllByStatusInOrderByShopNameAscIdAsc(any())).thenReturn(List.of(shop));
        when(identityApi.findUser(3L)).thenReturn(Optional.of(
                new UserSummary(3L, "Chủ shop", UserRole.USER, AccountStatus.ACTIVE)));

        var result = new AdminShopManagementService(shopRepository, identityApi).listManagedShops(99L);

        assertEquals(1, result.size());
        assertEquals("Chủ shop", result.getFirst().ownerName());
        verify(shopRepository).findAllByStatusInOrderByShopNameAscIdAsc(
                Set.of(ShopStatus.APPROVED, ShopStatus.LOCKED));
    }

    @Test
    void returnsLockedShopDetailWithApprovalAndLockActorNames() {
        Shop shop = mockShop(10L, ShopStatus.LOCKED);
        when(shop.getApprovedById()).thenReturn(7L);
        when(shop.getLockedById()).thenReturn(99L);
        when(shop.getLockReason()).thenReturn("Vi phạm chính sách");
        allowAdmin();
        when(shopRepository.findByIdAndStatusIn(10L, Set.of(ShopStatus.APPROVED, ShopStatus.LOCKED)))
                .thenReturn(Optional.of(shop));
        when(identityApi.findUser(3L)).thenReturn(Optional.of(user(3L, "Chủ shop")));
        when(identityApi.findUser(7L)).thenReturn(Optional.of(user(7L, "Người duyệt")));

        var detail = new AdminShopManagementService(shopRepository, identityApi)
                .getManagedShop(99L, 10L);

        assertEquals("Chủ shop", detail.ownerName());
        assertEquals("Người duyệt", detail.approvedByName());
        assertEquals("Admin", detail.lockedByName());
        assertEquals("Vi phạm chính sách", detail.lockReason());
    }

    @Test
    void rejectsInactiveAdminBeforeQueryingShops() {
        when(identityApi.findUser(99L)).thenReturn(Optional.of(new UserSummary(
                99L, "Admin", UserRole.ADMIN, AccountStatus.LOCKED)));

        assertThrows(ShopLockAuthorizationException.class,
                () -> new AdminShopManagementService(shopRepository, identityApi)
                        .listManagedShops(99L));

        verify(shopRepository, never()).findAllByStatusInOrderByShopNameAscIdAsc(any());
    }

    @Test
    void rejectsMissingOrUnmanagedShop() {
        allowAdmin();
        when(shopRepository.findByIdAndStatusIn(10L, Set.of(ShopStatus.APPROVED, ShopStatus.LOCKED)))
                .thenReturn(Optional.empty());

        assertThrows(ShopManagementNotFoundException.class,
                () -> new AdminShopManagementService(shopRepository, identityApi)
                        .getManagedShop(99L, 10L));
    }

    private void allowAdmin() {
        when(identityApi.findUser(99L)).thenReturn(Optional.of(
                new UserSummary(99L, "Admin", UserRole.ADMIN, AccountStatus.ACTIVE)));
    }

    private static UserSummary user(Long id, String name) {
        return new UserSummary(id, name, UserRole.USER, AccountStatus.ACTIVE);
    }

    private static Shop mockShop(Long id, ShopStatus status) {
        Shop shop = mock(Shop.class);
        when(shop.getId()).thenReturn(id);
        when(shop.getOwnerId()).thenReturn(3L);
        when(shop.getShopName()).thenReturn("Shop A");
        when(shop.getStatus()).thenReturn(status);
        when(shop.getApprovedAt()).thenReturn(Instant.parse("2026-10-10T10:00:00Z"));
        return shop;
    }
}
