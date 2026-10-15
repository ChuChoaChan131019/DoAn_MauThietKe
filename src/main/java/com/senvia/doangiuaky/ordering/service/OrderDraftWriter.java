package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.entity.*;
import com.senvia.doangiuaky.ordering.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import java.util.HashSet;
import java.util.Set;

@Service
public class OrderDraftWriter {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final MerchantApi merchantApi;

    public OrderDraftWriter(OrderRepository orders, OrderItemRepository items, PaymentRepository payments,
            MerchantApi merchantApi) {
        this.orders = orders;
        this.items = items;
        this.payments = payments;
        this.merchantApi = merchantApi;
    }

    // Ket qua khoi tao payment tu PAY 04
    public record PaymentSeed(PaymentStatus status, String transactionReference) {
    }

    @FunctionalInterface
    public interface PaymentSeedProvider {
        PaymentSeed initialize(PaymentMethod method, String orderCode, BigDecimal amount);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> save(PreparedCheckout checkout, PaymentSeedProvider paymentSeedProvider) {
        Objects.requireNonNull(checkout, "checkout");
        Objects.requireNonNull(paymentSeedProvider, "payment initializer");

        if (checkout.shopOrders().isEmpty()) {
            throw new CheckoutPreparationException("Không có gian hàng để tạo đơn.");
        }

        // Kiem tra shop trung truoc khi luu bat ky du lieu nao
        Set<Long> shopIds = new HashSet<>();

        for (PreparedCheckout.ShopOrder group : checkout.shopOrders()) {
            if (group == null
                    || group.shopId() == null
                    || group.shopId() <= 0) {
                throw new CheckoutPreparationException("Nhóm đơn hàng có shopId không hợp lệ."
                );
            }

            if (!shopIds.add(group.shopId())) {
                throw new CheckoutPreparationException("Gian hàng " + group.shopId() + " xuất hiện nhiều lần trong một checkout.");
            }
        }

        // Xac minh lai du lieu authoritative cua Merchant truoc khi ghi bat ky don hang nao.
        // Gia trong PreparedCheckout la quote server-side ma buyer vua xac nhan.
        BigDecimal currentGrandTotal = BigDecimal.ZERO;
        boolean priceChanged = false;

        for (PreparedCheckout.ShopOrder group : checkout.shopOrders()) {
            if (group.items() == null || group.items().isEmpty()) {
                throw new CheckoutPreparationException("Nhóm đơn hàng không có sản phẩm.");
            }

            for (PreparedCheckout.Line line : group.items()) {
                if (line == null
                        || line.productId() == null
                        || line.productId() <= 0
                        || line.quantity() <= 0
                        || line.unitPrice() == null
                        || line.unitPrice().signum() <= 0) {
                    throw new CheckoutPreparationException("Sản phẩm trong nhóm đơn hàng không hợp lệ.");
                }

                var product = merchantApi.findProductForCart(line.productId())
                        .orElseThrow(() -> new CheckoutPreparationException("Sản phẩm trong nhóm đơn hàng không tồn tại."));

                if (!line.productId().equals(product.productId())
                        || !group.shopId().equals(product.shopId())) {
                    throw new CheckoutPreparationException("Sản phẩm không thuộc gian hàng của đơn hàng.");
                }

                // CheckoutTransactionService đã kiểm tra saleability và trừ kho nguyên tử
                // qua MerchantStockApi trước khi gọi writer. Không kiểm tra saleability hoặc
                // số tồn tại đây vì sản phẩm vừa bán hết có stockQuantity = 0 là hợp lệ.
                currentGrandTotal = currentGrandTotal.add(
                        product.currentPrice().multiply(BigDecimal.valueOf(line.quantity())));
                if (product.currentPrice().compareTo(line.unitPrice()) != 0) {
                    priceChanged = true;
                }
            }
        }

        if (priceChanged) {
            throw new CheckoutPriceChangedException(currentGrandTotal);
        }

        List<Long> savedIds = new ArrayList<>();

        // 1. Kiem tra tong tien toan gio
        BigDecimal expectedGrandTotal = checkout
                .shopOrders()
                .stream()
                .map(PreparedCheckout.ShopOrder::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (expectedGrandTotal.compareTo(
                checkout.grandTotal()
        ) != 0) {
            throw new CheckoutPreparationException("Tổng tiền toàn giỏ không chính xác.");
        }

        // 2. Tao moi don cho moi shop
        for (var group : checkout.shopOrders()) {
            if (!checkout.buyerId().equals(group.buyerId())
                    || group.items().isEmpty()
                    || group.shopId() == null
                    || group.shopId() <= 0) {

                throw new CheckoutPreparationException("Nhóm đơn hàng không hợp lệ.");
            }

            // 3. Kiem tra lai thanh tien tung dong
            BigDecimal calculated = group.items()
                    .stream()
                    .map(line -> {
                        if (line.quantity() <= 0
                                || line.unitPrice().signum() <= 0
                                || line.subtotal().compareTo(
                                        line.unitPrice().multiply(
                                                BigDecimal.valueOf(
                                                        line.quantity()
                                                )
                                        )
                                ) != 0) {

                            throw new CheckoutPreparationException("Thành tiền dòng hàng không đúng.");
                        }
                        return line.subtotal();
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (calculated.compareTo(
                    group.totalAmount()
            ) != 0 || calculated.signum() <= 0) {
                throw new CheckoutPreparationException("Tổng tiền gian hàng không chính xác.");
            }

            // 4. Lay dia chi giao hang
            var delivery = checkout.delivery();

            // 5. Tao ma don hang duy nhat
            String code = "ORD-" +
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 24);

            // 6. Luu order
            Order order = orders.save(new Order(code, checkout.buyerId(), group.shopId(), delivery.receiverName(), delivery.receiverPhone(), delivery.shippingAddress(), delivery.note(), group.totalAmount()));

            // 7. Luu cac order_items
            for (var line : group.items()) {
                items.save(new OrderItem(order.getId(), line.productId(), line.productNameSnapshot(), line.unitPrice(), line.quantity(), line.subtotal()));
            }

            // 8. Khoi tao payment qua PAY 04
            PaymentSeed seed = Objects.requireNonNull(paymentSeedProvider.initialize( checkout.paymentMethod(), code, group.totalAmount()), "PAY 04 initialization is required");

            // 9. Luu payment
            payments.save(new Payment(order.getId(), checkout.paymentMethod(), group.totalAmount(), seed.status(), seed.transactionReference()));

            savedIds.add(order.getId());
        }

        // 10. Flush de phat hien loi database
        payments.flush();
        items.flush();
        orders.flush();

        return List.copyOf(savedIds);
    }
}
