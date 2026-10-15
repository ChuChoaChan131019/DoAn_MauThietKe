package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CheckoutQuote(String token, Instant createdAt, PreparedCheckout preview) {
    public static CheckoutQuote of(PreparedCheckout preview) {
        return new CheckoutQuote(UUID.randomUUID().toString(), Instant.now(), preview);
    }

    public boolean validToken(String submittedToken) {
        return token.equals(submittedToken)
                && !Instant.now().isAfter(createdAt.plus(Duration.ofMinutes(10)));
    }

    public List<Long> selectedItemIds() {
        return preview.purchasedCartItems()
                .stream()
                .map(item -> item.cartItemId())
                .toList();
    }

    public CheckoutPaymentForm toForm() {
        CheckoutPaymentForm form = new CheckoutPaymentForm();

        form.setReceiverName(preview.delivery().receiverName());
        form.setReceiverPhone(preview.delivery().receiverPhone());
        form.setShippingAddress(preview.delivery().shippingAddress());
        form.setNote(preview.delivery().note());
        form.setPaymentMethod(preview.paymentMethod());

        return form;
    }

    public void requireUnchanged(PreparedCheckout fresh) {

        if (!preview.buyerId().equals(fresh.buyerId())
                || preview.paymentMethod() != fresh.paymentMethod()
                || !preview.delivery().equals(fresh.delivery())
                || !preview.purchasedCartItems().equals(fresh.purchasedCartItems())
                || !preview.shopOrders().equals(fresh.shopOrders())
                || preview.grandTotal().compareTo(fresh.grandTotal()) != 0) {

            throw new CheckoutPreparationException("Giỏ hàng, giá hoặc thông tin sản phẩm đã thay đổi. " + "Vui lòng xem trước và xác nhận lại.");
        }
    }
}