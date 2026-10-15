package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.shopping.api.CheckoutCartItem;

import java.math.BigDecimal;
import java.util.List;

public record PreparedCheckout(Long buyerId, DeliverySnapshot delivery, PaymentMethod paymentMethod, List<ShopOrder> shopOrders, BigDecimal grandTotal, List<CheckoutCartItem> purchasedCartItems) {
    public PreparedCheckout {
        shopOrders = List.copyOf(shopOrders);
        purchasedCartItems = List.copyOf(purchasedCartItems);
    }

    public record DeliverySnapshot(String receiverName, String receiverPhone, String shippingAddress, String note) {
    }

    public record ShopOrder(Long buyerId, Long shopId, String shopNameSnapshot, List<Line> items, BigDecimal totalAmount) {
        public ShopOrder {
            items = List.copyOf(items);
        }

        public ShopOrder(Long buyerId, Long shopId, List<Line> items, BigDecimal totalAmount) {
            this(buyerId, shopId, "Gian hàng #" + shopId, items, totalAmount);
        }
    }

    public record Line(Long productId, String productNameSnapshot, BigDecimal unitPrice, int quantity, BigDecimal subtotal) {
    }
}