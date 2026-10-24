package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsAccessException;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.entity.Shop;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantStatisticsServiceTest {

    private final CurrentUserApi currentUser = mock(CurrentUserApi.class);
    private final ShopRepository shops = mock(ShopRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final MerchantStatisticsService service = new MerchantStatisticsService(currentUser, shops, products);

    @Test
    void countsManagedProductsForCurrentOwner() {
        Shop shop = mock(Shop.class);
        when(currentUser.requireCurrentUser()).thenReturn(new UserSummary(7L, "Owner", UserRole.USER, AccountStatus.ACTIVE));
        when(shops.findById(3L)).thenReturn(Optional.of(shop));
        when(shop.getOwnerId()).thenReturn(7L);
        when(products.countByShopId(3L)).thenReturn(4L);

        assertEquals(4, service.countProductsForShop(3L));
        verify(products).countByShopId(3L);
    }

    @Test
    void countsProductsForCurrentShopWithoutAcceptingClientShopId() {
        Shop shop = mock(Shop.class);
        when(currentUser.requireCurrentUser()).thenReturn(new UserSummary(7L, "Owner", UserRole.USER, AccountStatus.ACTIVE));
        when(shops.findByOwnerId(7L)).thenReturn(Optional.of(shop));
        when(shop.getId()).thenReturn(3L);
        when(products.countByShopId(3L)).thenReturn(4L);

        assertEquals(4, service.countProductsForCurrentShop());
        verify(products).countByShopId(3L);
    }

    @Test
    void rejectsCurrentUserWithoutShop() {
        when(currentUser.requireCurrentUser()).thenReturn(new UserSummary(7L, "Owner", UserRole.USER, AccountStatus.ACTIVE));

        assertThrows(MerchantStatisticsAccessException.class, service::countProductsForCurrentShop);
    }

    @Test
    void rejectsOtherOwnersAndLockedAccounts() {
        Shop shop = mock(Shop.class);
        when(shops.findById(3L)).thenReturn(Optional.of(shop));
        when(shop.getOwnerId()).thenReturn(9L);
        when(currentUser.requireCurrentUser()).thenReturn(new UserSummary(7L, "Owner", UserRole.USER, AccountStatus.ACTIVE));
        assertThrows(MerchantStatisticsAccessException.class, () -> service.countProductsForShop(3L));

        when(currentUser.requireCurrentUser()).thenReturn(new UserSummary(7L, "Owner", UserRole.USER, AccountStatus.LOCKED));
        assertThrows(MerchantStatisticsAccessException.class, () -> service.countProductsForShop(3L));
        assertThrows(MerchantStatisticsAccessException.class, service::countProductsForCurrentShop);
    }
}
