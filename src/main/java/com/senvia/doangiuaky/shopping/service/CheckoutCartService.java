package com.senvia.doangiuaky.shopping.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;
import com.senvia.doangiuaky.shopping.entity.Cart;
import com.senvia.doangiuaky.shopping.entity.CartItem;
import com.senvia.doangiuaky.shopping.repository.CartItemRepository;
import com.senvia.doangiuaky.shopping.repository.CartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CheckoutCartService implements CheckoutCartApi {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final IdentityApi identityApi;

    public CheckoutCartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            IdentityApi identityApi) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.identityApi = identityApi;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<CheckoutCartItem> getCheckoutSnapshot(Long buyerId) {
        validateBuyer(buyerId);
        Cart cart = lockedCart(buyerId);
        if (cart == null) {
            return List.of();
        }
        return cartItemRepository.findAllByCartIdOrderByIdAsc(cart.getId()).stream()
                .map(item -> new CheckoutCartItem(item.getId(), item.getProductId(), item.getQuantity()))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void removePurchasedItems(Long buyerId, List<CheckoutCartItem> purchasedItems) {
        validateBuyer(buyerId);
        List<CheckoutCartItem> requested = validateItems(purchasedItems);
        Cart cart = lockedCart(buyerId);
        if (cart == null) {
            throw new CartOperationException("Giỏ hàng không còn tồn tại.");
        }

        Map<Long, CartItem> ownedItems = new HashMap<>();
        for (CartItem item : cartItemRepository.findAllByCartIdOrderByIdAsc(cart.getId())) {
            ownedItems.put(item.getId(), item);
        }
        List<CartItem> toRemove = new ArrayList<>(requested.size());
        for (CheckoutCartItem snapshot : requested) {
            CartItem item = ownedItems.get(snapshot.cartItemId());
            if (item == null || !item.getProductId().equals(snapshot.productId())
                    || item.getQuantity() != snapshot.quantity()) {
                throw new CartOperationException("Mục trong giỏ đã thay đổi hoặc không thuộc người mua.");
            }
            toRemove.add(item);
        }

        cartItemRepository.deleteAll(toRemove);
        cartItemRepository.flush();
    }

    private Cart lockedCart(Long buyerId) {
        Cart cart = cartRepository.findByUserIdForUpdate(buyerId).orElse(null);
        if (cart != null && !buyerId.equals(cart.getUserId())) {
            throw new CartOperationException("Giỏ hàng không thuộc người mua.");
        }
        return cart;
    }

    private void validateBuyer(Long buyerId) {
        if (buyerId == null || buyerId <= 0) {
            throw new CartOperationException("Không xác định được người mua.");
        }
        UserSummary buyer = identityApi.findUser(buyerId)
                .orElseThrow(() -> new CartOperationException("Tài khoản không tồn tại."));
        if (!buyerId.equals(buyer.userId()) || buyer.role() != UserRole.USER
                || buyer.accountStatus() != AccountStatus.ACTIVE) {
            throw new CartOperationException("Tài khoản không được phép mua hàng.");
        }
    }

    private static List<CheckoutCartItem> validateItems(List<CheckoutCartItem> purchasedItems) {
        if (purchasedItems == null || purchasedItems.isEmpty()) {
            throw new CartOperationException("Danh sách mục cần xóa không hợp lệ.");
        }
        List<CheckoutCartItem> requested = new ArrayList<>(purchasedItems.size());
        Set<Long> ids = new HashSet<>();
        for (CheckoutCartItem item : purchasedItems) {
            if (item == null || item.cartItemId() == null || item.cartItemId() <= 0
                    || item.productId() == null || item.productId() <= 0
                    || item.quantity() <= 0 || !ids.add(item.cartItemId())) {
                throw new CartOperationException("Danh sách mục cần xóa không hợp lệ hoặc có mục trùng.");
            }
            requested.add(item);
        }
        return requested;
    }
}
