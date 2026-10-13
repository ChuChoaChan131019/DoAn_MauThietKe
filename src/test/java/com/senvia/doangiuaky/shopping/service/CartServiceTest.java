package com.senvia.doangiuaky.shopping.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.shopping.dto.CartItemMutationResult;
import com.senvia.doangiuaky.shopping.dto.CartView;
import com.senvia.doangiuaky.shopping.entity.Cart;
import com.senvia.doangiuaky.shopping.entity.CartItem;
import com.senvia.doangiuaky.shopping.repository.CartItemRepository;
import com.senvia.doangiuaky.shopping.repository.CartRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private static final Long BUYER_ID = 10L;
    private static final Long PRODUCT_ID = 20L;

    @Test
    void activeUserCreatesOneCartAndOneItemForSaleableProduct() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(5, 120000)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.empty());
        when(carts.save(any(Cart.class))).thenReturn(cart);
        when(items.findByCartIdAndProductId(99L, PRODUCT_ID)).thenReturn(Optional.empty());

        CartService service = new CartService(carts, items, identity, merchant);

        CartItemMutationResult result = service.addItem(BUYER_ID, PRODUCT_ID, 2);

        assertEquals(2, result.item().quantity());
        assertEquals(new BigDecimal("240000"), result.item().subtotal());
        assertTrue(!result.quantityClamped());
        verify(carts).save(any(Cart.class));
        verify(items).save(any(CartItem.class));
    }

    @Test
    void existingItemIsIncreasedAndClampedToCurrentStock() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem existing = new CartItem(99L, PRODUCT_ID, 3);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(5, 120000)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByCartIdAndProductId(99L, PRODUCT_ID)).thenReturn(Optional.of(existing));

        CartItemMutationResult result = new CartService(carts, items, identity, merchant)
                .addItem(BUYER_ID, PRODUCT_ID, 4);

        assertEquals(5, existing.getQuantity());
        assertEquals(5, result.item().quantity());
        assertTrue(result.quantityClamped());
        verify(items).save(existing);
    }

    @Test
    void adminCannotAddEvenWhenRequestCallsServiceDirectly() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.ADMIN, AccountStatus.ACTIVE)));

        CartService service = new CartService(carts, items, identity, merchant);

        assertThrows(CartOperationException.class, () -> service.addItem(BUYER_ID, PRODUCT_ID, 1));
        verify(merchant, never()).findProductForCartMutation(any());
        verify(carts, never()).save(any());
    }

    @Test
    void lockedUserAndSelfPurchaseAreRejectedWithoutWritingCart() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.LOCKED)));
        CartService service = new CartService(carts, items, identity, merchant);

        assertThrows(CartOperationException.class, () -> service.addItem(BUYER_ID, PRODUCT_ID, 1));
        verify(carts, never()).save(any());

        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(5, 120000, BUYER_ID)));
        assertThrows(CartOperationException.class, () -> service.addItem(BUYER_ID, PRODUCT_ID, 1));
        verify(items, never()).save(any());
    }

    @Test
    void unsaleableProductIsRejectedBeforeCartWrite() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(
                new CartProductView(PRODUCT_ID, 30L, 40L, "Shop", "Product", null,
                        new BigDecimal("120000"), 0, true, true, true, true)));

        assertThrows(CartOperationException.class,
                () -> new CartService(carts, items, identity, merchant).addItem(BUYER_ID, PRODUCT_ID, 1));
        verify(items, never()).save(any());
    }

    @Test
    void updateQuantityUsesAbsoluteValueAndClampsToLockedStock() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem existing = new CartItem(99L, PRODUCT_ID, 2);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByCartIdAndProductId(99L, PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(5, 120000)));

        CartItemMutationResult result = new CartService(carts, items, identity, merchant)
                .updateQuantity(BUYER_ID, PRODUCT_ID, 9);

        assertEquals(5, existing.getQuantity());
        assertTrue(result.quantityClamped());
        assertTrue(result.message().contains("5"));
        verify(items).save(existing);
    }

    @Test
    void updateQuantityBelowStockPersistsRequestedAbsoluteValue() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem existing = new CartItem(99L, PRODUCT_ID, 4);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByCartIdAndProductId(99L, PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(5, 120000)));

        CartItemMutationResult result = new CartService(carts, items, identity, merchant)
                .updateQuantity(BUYER_ID, PRODUCT_ID, 2);

        assertEquals(2, existing.getQuantity());
        assertFalse(result.quantityClamped());
        verify(items).save(existing);
    }

    @Test
    void updateOfAnotherCartOrUnavailableProductDoesNotChangeExistingQuantity() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem existing = new CartItem(99L, PRODUCT_ID, 2);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByCartIdAndProductId(99L, PRODUCT_ID)).thenReturn(Optional.empty());

        CartService service = new CartService(carts, items, identity, merchant);
        assertThrows(CartOperationException.class,
                () -> service.updateQuantity(BUYER_ID, PRODUCT_ID, 3));
        verify(merchant, never()).findProductForCartMutation(any());

        when(items.findByCartIdAndProductId(99L, PRODUCT_ID)).thenReturn(Optional.of(existing));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(0, 120000)));
        assertThrows(CartOperationException.class,
                () -> service.updateQuantity(BUYER_ID, PRODUCT_ID, 3));
        assertEquals(2, existing.getQuantity());
        verify(items, never()).save(any());
    }

    @Test
    void nullZeroAndNegativeQuantitiesAreRejected() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        CartService service = new CartService(carts, items, identity, merchant);

        assertThrows(CartOperationException.class, () -> service.addItem(BUYER_ID, PRODUCT_ID, null));
        assertThrows(CartOperationException.class, () -> service.updateQuantity(BUYER_ID, PRODUCT_ID, 0));
        assertThrows(CartOperationException.class, () -> service.updateQuantity(BUYER_ID, PRODUCT_ID, -1));
        verify(items, never()).save(any());
    }

    @Test
    void cartViewUsesCurrentMerchantPriceInsteadOfCartSnapshot() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserId(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findAllByCartIdOrderByIdAsc(99L)).thenReturn(java.util.List.of(new CartItem(99L, PRODUCT_ID, 2)));
        when(merchant.findProductForCart(PRODUCT_ID)).thenReturn(Optional.of(product(5, 135000)));

        CartView view = new CartService(carts, items, identity, merchant).getCart(BUYER_ID);

        assertEquals(new BigDecimal("270000"), view.subtotal());
        assertEquals(new BigDecimal("135000"), view.shops().getFirst().items().getFirst().currentPrice());
    }

    @Test
    void cartWithOnlyUnavailableItemsIsNotEmptyButHasNoCheckoutableItems() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserId(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findAllByCartIdOrderByIdAsc(99L))
                .thenReturn(java.util.List.of(new CartItem(99L, PRODUCT_ID, 2)));
        when(merchant.findProductForCart(PRODUCT_ID)).thenReturn(Optional.of(product(0, 135000)));

        CartView view = new CartService(carts, items, identity, merchant).getCart(BUYER_ID);

        assertFalse(view.isEmpty());
        assertFalse(view.hasCheckoutableItems());
        assertEquals(0, view.totalQuantity());
        assertFalse(view.shops().getFirst().items().getFirst().available());
    }

    @Test
    void stockDecreaseAfterMutationShowsAdjustableWarningWithoutChangingCartItem() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem existing = new CartItem(99L, PRODUCT_ID, 5);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserId(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findAllByCartIdOrderByIdAsc(99L)).thenReturn(java.util.List.of(existing));
        when(merchant.findProductForCart(PRODUCT_ID)).thenReturn(Optional.of(product(2, 120000)));

        CartView view = new CartService(carts, items, identity, merchant).getCart(BUYER_ID);

        var item = view.shops().getFirst().items().getFirst();
        assertFalse(item.available());
        assertTrue(item.quantityAdjustable());
        assertEquals(2, item.stockQuantity());
        assertEquals(5, existing.getQuantity());
        verify(items, never()).save(any());
    }

    private static UserSummary user(Long id, UserRole role, AccountStatus status) {
        return new UserSummary(id, "Test User", role, status);
    }

    private static CartProductView product(int stock, int price) {
        return product(stock, price, 40L);
    }

    private static CartProductView product(int stock, int price, Long ownerId) {
        return new CartProductView(PRODUCT_ID, 30L, ownerId, "Shop", "Product", null,
                BigDecimal.valueOf(price), stock, true, true, true, true);
    }

    private static Cart mockCart(Long id) {
        Cart cart = mock(Cart.class);
        when(cart.getId()).thenReturn(id);
        return cart;
    }
}
