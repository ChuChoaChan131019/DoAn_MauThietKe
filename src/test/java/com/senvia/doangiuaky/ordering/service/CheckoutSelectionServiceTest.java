package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.ShopOrderCapabilities;
import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CheckoutSelectionServiceTest {

    private final CheckoutCartApi cartApi = mock(CheckoutCartApi.class);
    private final MerchantApi merchantApi = mock(MerchantApi.class);
    private final CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
    private final CheckoutSelectionService service =
            new CheckoutSelectionService(cartApi, merchantApi, currentUserApi);

    @BeforeEach
    void setUp() {
        when(currentUserApi.requireCurrentUser()).thenReturn(
                new UserSummary(1L, "Buyer", UserRole.USER, AccountStatus.ACTIVE));
        when(cartApi.getCheckoutSnapshot()).thenReturn(List.of(
                new CheckoutCartItem(10L, 100L, 2),
                new CheckoutCartItem(11L, 101L, 1)));
        when(merchantApi.findProductForCart(100L)).thenReturn(Optional.of(product(100L, 5)));
        when(merchantApi.findProductForCart(101L)).thenReturn(Optional.of(product(101L, 0)));
        when(merchantApi.findShopOrderCapabilities(20L))
                .thenReturn(Optional.of(new ShopOrderCapabilities(20L, true, true)));
    }

    @Test
    void previewsOnlySelectedValidItemAndIgnoresInvalidUnselectedItem() {
        var preview = service.preview(List.of("10"));

        assertEquals(1, preview.shops().size());
        assertEquals(List.of(10L), preview.shops().getFirst().items().stream()
                .map(item -> item.cartItemId()).toList());
        assertEquals(new BigDecimal("200.00"), preview.subtotal());
    }

    @Test
    void rejectsSelectedUnavailableAndForeignItems() {
        assertThrows(CheckoutSelectionException.class, () -> service.preview(List.of("11")));
        assertThrows(CheckoutSelectionException.class, () -> service.preview(List.of("999")));
        assertThrows(CheckoutSelectionException.class, () -> service.preview(List.of("10", "11")));
    }

    @Test
    void rejectsEmptyDuplicateAndMalformedSelection() {
        assertThrows(CheckoutSelectionException.class, () -> service.preview(null));
        assertThrows(CheckoutSelectionException.class, () -> service.preview(List.of("10", "10")));
        assertThrows(CheckoutSelectionException.class, () -> service.preview(List.of("abc")));
    }

    private static CartProductView product(Long id, int stock) {
        return new CartProductView(id, 20L, 30L, "Shop", "Product " + id, null,
                new BigDecimal("100.00"), stock, true, true, true, true);
    }
}
