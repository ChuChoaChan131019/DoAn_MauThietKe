package com.senvia.doangiuaky.shopping.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.shopping.dto.FavoriteItemView;
import com.senvia.doangiuaky.shopping.entity.Favorite;
import com.senvia.doangiuaky.shopping.repository.FavoriteRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavoriteServiceTest {

    private static final Long USER_ID = 10L;
    private static final Long PRODUCT_ID = 20L;

    @Test
    void activeUserCanAddExistingProduct() {
        FavoriteRepository favorites = mock(FavoriteRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        when(identity.findUser(USER_ID)).thenReturn(Optional.of(user(UserRole.USER, AccountStatus.ACTIVE)));
        when(merchant.findProductForCart(PRODUCT_ID)).thenReturn(Optional.of(product(true, 5)));

        new FavoriteService(favorites, identity, merchant).addFavorite(USER_ID, PRODUCT_ID);

        verify(favorites).saveAndFlush(any(Favorite.class));
    }

    @Test
    void addingExistingFavoriteIsIdempotent() {
        FavoriteRepository favorites = mock(FavoriteRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        when(identity.findUser(USER_ID)).thenReturn(Optional.of(user(UserRole.USER, AccountStatus.ACTIVE)));
        when(merchant.findProductForCart(PRODUCT_ID)).thenReturn(Optional.of(product(true, 5)));
        when(favorites.existsByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(true);

        new FavoriteService(favorites, identity, merchant).addFavorite(USER_ID, PRODUCT_ID);

        verify(favorites, never()).saveAndFlush(any());
    }

    @Test
    void removalUsesBothCurrentUserAndProductAndIsSafeWhenMissing() {
        FavoriteRepository favorites = mock(FavoriteRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        when(identity.findUser(USER_ID)).thenReturn(Optional.of(user(UserRole.USER, AccountStatus.ACTIVE)));
        when(favorites.deleteByUserIdAndProductId(USER_ID, PRODUCT_ID)).thenReturn(0L);

        new FavoriteService(favorites, identity, merchant).removeFavorite(USER_ID, PRODUCT_ID);

        verify(favorites).deleteByUserIdAndProductId(USER_ID, PRODUCT_ID);
    }

    @Test
    void invalidActorAndMissingProductAreRejectedBeforeWrite() {
        FavoriteRepository favorites = mock(FavoriteRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        FavoriteService service = new FavoriteService(favorites, identity, merchant);

        when(identity.findUser(USER_ID)).thenReturn(Optional.of(user(UserRole.ADMIN, AccountStatus.ACTIVE)));
        assertThrows(FavoriteOperationException.class,
                () -> service.addFavorite(USER_ID, PRODUCT_ID));

        when(identity.findUser(USER_ID)).thenReturn(Optional.of(user(UserRole.USER, AccountStatus.LOCKED)));
        assertThrows(FavoriteOperationException.class,
                () -> service.addFavorite(USER_ID, PRODUCT_ID));

        when(identity.findUser(USER_ID)).thenReturn(Optional.of(user(UserRole.USER, AccountStatus.ACTIVE)));
        when(merchant.findProductForCart(PRODUCT_ID)).thenReturn(Optional.empty());
        assertThrows(FavoriteOperationException.class,
                () -> service.addFavorite(USER_ID, PRODUCT_ID));

        verify(favorites, never()).saveAndFlush(any());
    }

    @Test
    void listKeepsUnsaleableAndMissingProductsAsUnavailable() {
        FavoriteRepository favorites = mock(FavoriteRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Favorite first = new Favorite(USER_ID, PRODUCT_ID);
        Favorite second = new Favorite(USER_ID, 21L);
        when(identity.findUser(USER_ID)).thenReturn(Optional.of(user(UserRole.USER, AccountStatus.ACTIVE)));
        when(favorites.findAllByUserIdOrderByCreatedAtDescIdDesc(USER_ID)).thenReturn(List.of(first, second));
        when(merchant.findProductForCart(PRODUCT_ID)).thenReturn(Optional.of(product(true, 0)));
        when(merchant.findProductForCart(21L)).thenReturn(Optional.empty());

        List<FavoriteItemView> result = new FavoriteService(favorites, identity, merchant).listForUser(USER_ID);

        assertEquals(2, result.size());
        assertFalse(result.get(0).available());
        assertEquals("Sản phẩm đã hết hàng.", result.get(0).unavailableReason());
        assertFalse(result.get(1).available());
        assertEquals("Sản phẩm không còn tồn tại", result.get(1).productName());
    }

    private static UserSummary user(UserRole role, AccountStatus status) {
        return new UserSummary(USER_ID, "Test User", role, status);
    }

    private static CartProductView product(boolean active, int stock) {
        return new CartProductView(
                PRODUCT_ID, 30L, 40L, "Shop", "Product", null,
                new BigDecimal("100000"), stock, true, true, true, active);
    }
}
