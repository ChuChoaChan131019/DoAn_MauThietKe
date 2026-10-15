package com.senvia.doangiuaky.shopping.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.shopping.dto.CartItemMutationResult;
import com.senvia.doangiuaky.shopping.dto.CartView;
import com.senvia.doangiuaky.shopping.dto.CartItemView;
import com.senvia.doangiuaky.shopping.entity.Cart;
import com.senvia.doangiuaky.shopping.entity.CartItem;
import com.senvia.doangiuaky.shopping.repository.CartItemRepository;
import com.senvia.doangiuaky.shopping.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    void updateItemQuantityUsesAbsoluteValueAndClampsToLockedStock() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem existing = new CartItem(77L, PRODUCT_ID, 2);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByIdAndCartId(77L, 99L)).thenReturn(Optional.of(existing));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(5, 120000)));

        CartItemMutationResult result = new CartService(carts, items, identity, merchant)
                .updateItemQuantity(BUYER_ID, 77L, 9);

        assertEquals(5, existing.getQuantity());
        assertTrue(result.quantityClamped());
        assertTrue(result.message().contains("5"));
        verify(items).save(existing);
        InOrder lockOrder = inOrder(carts, items, merchant);
        lockOrder.verify(carts).findByUserIdForUpdate(BUYER_ID);
        lockOrder.verify(items).findByIdAndCartId(77L, 99L);
        lockOrder.verify(merchant).findProductForCartMutation(PRODUCT_ID);
    }

    @Test
    void updateItemQuantityBelowStockPersistsRequestedAbsoluteValue() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem existing = new CartItem(77L, PRODUCT_ID, 4);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByIdAndCartId(77L, 99L)).thenReturn(Optional.of(existing));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(5, 120000)));

        CartItemMutationResult result = new CartService(carts, items, identity, merchant)
                .updateItemQuantity(BUYER_ID, 77L, 2);

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
        CartItem existing = new CartItem(77L, PRODUCT_ID, 2);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByIdAndCartId(77L, 99L)).thenReturn(Optional.empty());

        CartService service = new CartService(carts, items, identity, merchant);
        assertThrows(CartOperationException.class,
                () -> service.updateItemQuantity(BUYER_ID, 77L, 3));
        verify(merchant, never()).findProductForCartMutation(any());

        when(items.findByIdAndCartId(77L, 99L)).thenReturn(Optional.of(existing));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(0, 120000)));
        assertThrows(CartOperationException.class,
                () -> service.updateItemQuantity(BUYER_ID, 77L, 3));
        assertEquals(2, existing.getQuantity());
        verify(items, never()).save(any());
    }

    @Test
    void updateOfMissingProductDoesNotChangeExistingQuantity() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem existing = new CartItem(77L, PRODUCT_ID, 2);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(
                user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByIdAndCartId(77L, 99L)).thenReturn(Optional.of(existing));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThrows(CartOperationException.class,
                () -> new CartService(carts, items, identity, merchant)
                        .updateItemQuantity(BUYER_ID, 77L, 3));

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
        assertThrows(CartOperationException.class, () -> service.updateItemQuantity(BUYER_ID, 77L, 0));
        assertThrows(CartOperationException.class, () -> service.updateItemQuantity(BUYER_ID, 77L, -1));
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
    void cartViewGroupsItemsByShopAndCalculatesEverySubtotalWithBigDecimal() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(
                user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserId(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findAllByCartIdOrderByIdAsc(99L)).thenReturn(List.of(
                new CartItem(99L, 20L, 2),
                new CartItem(99L, 21L, 3),
                new CartItem(99L, 22L, 4)));
        when(merchant.findProductForCart(20L)).thenReturn(Optional.of(
                product(20L, 30L, "Shop A", "Product A", new BigDecimal("10.25"))));
        when(merchant.findProductForCart(21L)).thenReturn(Optional.of(
                product(21L, 30L, "Shop A", "Product B", new BigDecimal("5.10"))));
        when(merchant.findProductForCart(22L)).thenReturn(Optional.of(
                product(22L, 31L, "Shop B", "Product C", new BigDecimal("1.25"))));

        CartView view = new CartService(carts, items, identity, merchant).getCart(BUYER_ID);

        assertEquals(2, view.shops().size());
        assertEquals(30L, view.shops().get(0).shopId());
        assertEquals(List.of(20L, 21L), view.shops().get(0).items().stream()
                .map(item -> item.productId()).toList());
        assertEquals(new BigDecimal("20.50"), view.shops().get(0).items().get(0).subtotal());
        assertEquals(new BigDecimal("15.30"), view.shops().get(0).items().get(1).subtotal());
        assertEquals(new BigDecimal("35.80"), view.shops().get(0).subtotal());
        assertEquals(31L, view.shops().get(1).shopId());
        assertEquals(new BigDecimal("5.00"), view.shops().get(1).subtotal());
        assertEquals(new BigDecimal("40.80"), view.subtotal());
        assertEquals(9, view.totalQuantity());
    }

    @Test
    void cartViewReloadReadsNewMerchantPriceWithoutChangingCartItem() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem item = new CartItem(99L, PRODUCT_ID, 2);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(
                user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserId(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findAllByCartIdOrderByIdAsc(99L)).thenReturn(List.of(item));
        when(merchant.findProductForCart(PRODUCT_ID)).thenReturn(
                Optional.of(product(PRODUCT_ID, 30L, "Shop", "Product", new BigDecimal("10.00"))),
                Optional.of(product(PRODUCT_ID, 30L, "Shop", "Product", new BigDecimal("12.50"))));
        CartService service = new CartService(carts, items, identity, merchant);

        CartView firstLoad = service.getCart(BUYER_ID);
        CartView secondLoad = service.getCart(BUYER_ID);

        assertEquals(new BigDecimal("10.00"), firstLoad.shops().getFirst().items().getFirst().currentPrice());
        assertEquals(new BigDecimal("20.00"), firstLoad.subtotal());
        assertEquals(new BigDecimal("12.50"), secondLoad.shops().getFirst().items().getFirst().currentPrice());
        assertEquals(new BigDecimal("25.00"), secondLoad.subtotal());
        assertEquals(2, item.getQuantity());
        verify(merchant, times(2)).findProductForCart(PRODUCT_ID);
        verify(items, never()).save(item);
    }

    @Test
    void missingCartReturnsEmptyViewWithZeroSubtotal() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(
                user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserId(BUYER_ID)).thenReturn(Optional.empty());

        CartView view = new CartService(carts, items, identity, merchant).getCart(BUYER_ID);

        assertTrue(view.shops().isEmpty());
        assertEquals(0, view.totalQuantity());
        assertEquals(BigDecimal.ZERO, view.subtotal());
        verify(items, never()).findAllByCartIdOrderByIdAsc(any());
        verify(merchant, never()).findProductForCart(any());
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

    @Test
    void updateAndRemoveUseLockedOwnedCartItem() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        CartItem item = new CartItem(99L, PRODUCT_ID, 2);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(
                user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByIdAndCartId(77L, 99L)).thenReturn(Optional.of(item));
        when(merchant.findProductForCartMutation(PRODUCT_ID)).thenReturn(Optional.of(product(5, 120000)));
        CartService service = new CartService(carts, items, identity, merchant);

        service.updateItemQuantity(BUYER_ID, 77L, 4);
        assertEquals(4, item.getQuantity());
        verify(items).save(item);

        service.removeItem(BUYER_ID, 77L);
        verify(items).delete(item);
        verify(items).flush();
        verify(carts, org.mockito.Mockito.times(2)).findByUserIdForUpdate(BUYER_ID);
    }

    @Test
    void foreignCartItemIsHiddenAsNotFoundAndNeverChanged() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        Cart cart = mockCart(99L);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(
                user(BUYER_ID, UserRole.USER, AccountStatus.ACTIVE)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(items.findByIdAndCartId(777L, 99L)).thenReturn(Optional.empty());
        CartService service = new CartService(carts, items, identity, merchant);

        assertThrows(CartItemNotFoundException.class,
                () -> service.updateItemQuantity(BUYER_ID, 777L, 2));
        assertThrows(CartItemNotFoundException.class,
                () -> service.removeItem(BUYER_ID, 777L));
        verify(items, never()).save(any());
        verify(items, never()).delete(any());
    }

    @Test
    void lockedUserCannotUpdateOrRemoveCartItemEvenThroughDirectServiceCall() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        MerchantApi merchant = mock(MerchantApi.class);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(
                user(BUYER_ID, UserRole.USER, AccountStatus.LOCKED)));
        CartService service = new CartService(carts, items, identity, merchant);

        assertThrows(CartOperationException.class,
                () -> service.updateItemQuantity(BUYER_ID, 77L, 2));
        assertThrows(CartOperationException.class,
                () -> service.removeItem(BUYER_ID, 77L));

        verify(carts, never()).findByUserIdForUpdate(any());
        verify(items, never()).save(any());
        verify(items, never()).delete(any());
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

    private static CartProductView product(
            Long productId,
            Long shopId,
            String shopName,
            String productName,
            BigDecimal price) {
        return new CartProductView(productId, shopId, 40L, shopName, productName, null,
                price, 100, true, true, true, true);
    }

    private static Cart mockCart(Long id) {
        Cart cart = mock(Cart.class);
        when(cart.getId()).thenReturn(id);
        return cart;
    }
}
