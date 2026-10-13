package com.senvia.doangiuaky.shopping.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;
import com.senvia.doangiuaky.shopping.entity.Cart;
import com.senvia.doangiuaky.shopping.entity.CartItem;
import com.senvia.doangiuaky.shopping.repository.CartItemRepository;
import com.senvia.doangiuaky.shopping.repository.CartRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CheckoutCartServiceTest {

    private static final Long BUYER_ID = 10L;

    @Test
    void snapshotContainsOnlyItemsFromTheLockedBuyerCart() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        Cart cart = mock(Cart.class);
        CartItem first = item(101L, 201L, 2);
        CartItem second = item(102L, 202L, 3);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(activeBuyer(BUYER_ID)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(cart.getId()).thenReturn(99L);
        when(cart.getUserId()).thenReturn(BUYER_ID);
        when(items.findAllByCartIdOrderByIdAsc(99L)).thenReturn(List.of(first, second));

        List<CheckoutCartItem> snapshot = new CheckoutCartService(carts, items, identity)
                .getCheckoutSnapshot(BUYER_ID);

        assertEquals(List.of(
                new CheckoutCartItem(101L, 201L, 2),
                new CheckoutCartItem(102L, 202L, 3)), snapshot);
        verify(carts).findByUserIdForUpdate(BUYER_ID);
        verify(items).findAllByCartIdOrderByIdAsc(99L);
    }

    @Test
    void bothOperationsRejectInvalidMissingAdminLockedAndMismatchedBuyersBeforeCartAccess() {
        assertBuyerRejected(null, null);
        assertBuyerRejected(0L, null);
        assertBuyerRejected(BUYER_ID, Optional.empty());
        assertBuyerRejected(BUYER_ID,
                Optional.of(new UserSummary(BUYER_ID, "Admin", UserRole.ADMIN, AccountStatus.ACTIVE)));
        assertBuyerRejected(BUYER_ID,
                Optional.of(new UserSummary(BUYER_ID, "Locked", UserRole.USER, AccountStatus.LOCKED)));
        assertBuyerRejected(BUYER_ID, Optional.of(activeBuyer(BUYER_ID + 1)));
    }

    @Test
    void foreignCartOwnershipIsRejectedBeforeItemsAreRead() {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        Cart cart = mock(Cart.class);
        when(identity.findUser(BUYER_ID)).thenReturn(Optional.of(activeBuyer(BUYER_ID)));
        when(carts.findByUserIdForUpdate(BUYER_ID)).thenReturn(Optional.of(cart));
        when(cart.getUserId()).thenReturn(BUYER_ID + 1);

        CheckoutCartService service = new CheckoutCartService(carts, items, identity);

        assertThrows(CartOperationException.class, () -> service.getCheckoutSnapshot(BUYER_ID));
        verify(items, never()).findAllByCartIdOrderByIdAsc(org.mockito.ArgumentMatchers.any());
    }

    private static void assertBuyerRejected(Long buyerId, Optional<UserSummary> user) {
        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        IdentityApi identity = mock(IdentityApi.class);
        if (buyerId != null && buyerId > 0) {
            when(identity.findUser(buyerId)).thenReturn(user);
        }

        CheckoutCartService service = new CheckoutCartService(carts, items, identity);

        assertThrows(CartOperationException.class, () -> service.getCheckoutSnapshot(buyerId));
        assertThrows(CartOperationException.class, () -> service.removePurchasedItems(
                buyerId, List.of(new CheckoutCartItem(1L, 1L, 1))));
        verifyNoInteractions(carts, items);
    }

    private static UserSummary activeBuyer(Long buyerId) {
        return new UserSummary(buyerId, "Buyer", UserRole.USER, AccountStatus.ACTIVE);
    }

    private static CartItem item(Long itemId, Long productId, int quantity) {
        CartItem item = mock(CartItem.class);
        when(item.getId()).thenReturn(itemId);
        when(item.getProductId()).thenReturn(productId);
        when(item.getQuantity()).thenReturn(quantity);
        return item;
    }
}
