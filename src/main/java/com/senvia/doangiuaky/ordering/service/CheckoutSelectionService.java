package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.dto.CheckoutSelectionView;
import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CheckoutSelectionService {

    private final CheckoutCartApi checkoutCartApi;
    private final MerchantApi merchantApi;
    private final CurrentUserApi currentUserApi;

    public CheckoutSelectionService(
            CheckoutCartApi checkoutCartApi, MerchantApi merchantApi, CurrentUserApi currentUserApi) {
        this.checkoutCartApi = checkoutCartApi;
        this.merchantApi = merchantApi;
        this.currentUserApi = currentUserApi;
    }

    @Transactional
    public CheckoutSelectionView preview(List<String> rawItemIds) {
        if (rawItemIds == null || rawItemIds.isEmpty()) {
            throw new CheckoutSelectionException("Vui lòng chọn ít nhất một sản phẩm hợp lệ trong giỏ hàng.");
        }

        List<Long> selectedIds = new ArrayList<>(rawItemIds.size());
        Set<Long> uniqueIds = new HashSet<>();
        for (String rawId : rawItemIds) {
            Long id;
            try {
                if (rawId == null) {
                    throw new NumberFormatException("null id");
                }
                id = Long.valueOf(rawId);
            } catch (NumberFormatException exception) {
                throw new CheckoutSelectionException("Lựa chọn sản phẩm không hợp lệ.");
            }
            if (id <= 0 || !uniqueIds.add(id)) {
                throw new CheckoutSelectionException("Lựa chọn sản phẩm không hợp lệ hoặc bị trùng.");
            }
            selectedIds.add(id);
        }

        Map<Long, CheckoutCartItem> ownedItems = new HashMap<>();
        for (CheckoutCartItem item : checkoutCartApi.getCheckoutSnapshot()) {
            ownedItems.put(item.cartItemId(), item);
        }

        Long buyerId = currentUserApi.requireCurrentUser().userId();
        Map<Long, List<CheckoutSelectionView.Item>> byShop = new LinkedHashMap<>();
        Map<Long, String> shopNames = new HashMap<>();
        Set<Long> checkedShops = new HashSet<>();
        for (Long selectedId : selectedIds) {
            CheckoutCartItem item = ownedItems.get(selectedId);
            if (item == null || item.quantity() <= 0) {
                throw new CheckoutSelectionException("Sản phẩm được chọn không còn trong giỏ hàng của bạn.");
            }
            CartProductView product = merchantApi.findProductForCart(item.productId())
                    .orElseThrow(() -> new CheckoutSelectionException(
                            "Sản phẩm được chọn không còn tồn tại. Vui lòng kiểm tra lại giỏ hàng."));
            if (!item.productId().equals(product.productId()) || !product.isSaleable()
                    || item.quantity() > product.stockQuantity()
                    || product.shopId() == null || product.shopOwnerId() == null
                    || product.productName() == null || product.productName().isBlank()
                    || product.shopName() == null || product.shopName().isBlank()
                    || buyerId.equals(product.shopOwnerId())) {
                throw new CheckoutSelectionException(
                        "Sản phẩm được chọn không còn hợp lệ. Vui lòng kiểm tra lại giỏ hàng.");
            }
            if (checkedShops.add(product.shopId())
                    && merchantApi.findShopOrderCapabilities(product.shopId())
                    .filter(capabilities -> capabilities.canReceiveNewOrders()
                            && product.shopId().equals(capabilities.shopId()))
                    .isEmpty()) {
                throw new CheckoutSelectionException(
                        "Gian hàng của sản phẩm được chọn không thể nhận đơn mới.");
            }
            BigDecimal subtotal = product.currentPrice().multiply(BigDecimal.valueOf(item.quantity()));
            byShop.computeIfAbsent(product.shopId(), ignored -> new ArrayList<>())
                    .add(new CheckoutSelectionView.Item(item.cartItemId(), product.productName(),
                            item.quantity(), subtotal));
            shopNames.putIfAbsent(product.shopId(), product.shopName());
        }

        List<CheckoutSelectionView.Shop> shops = byShop.entrySet().stream()
                .map(entry -> new CheckoutSelectionView.Shop(entry.getKey(), shopNames.get(entry.getKey()),
                        List.copyOf(entry.getValue()), entry.getValue().stream()
                                .map(CheckoutSelectionView.Item::subtotal)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
        BigDecimal subtotal = shops.stream()
                .map(CheckoutSelectionView.Shop::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CheckoutSelectionView(shops, subtotal);
    }
}
