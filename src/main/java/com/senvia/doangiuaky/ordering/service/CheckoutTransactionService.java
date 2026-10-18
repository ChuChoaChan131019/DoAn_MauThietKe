package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.merchant.api.MerchantStockApi;
import com.senvia.doangiuaky.ordering.api.OrderCreatedEvent;
import com.senvia.doangiuaky.ordering.dto.PaymentDetails;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class CheckoutTransactionService {
    private final CheckoutPreparationService preparation;
    private final MerchantStockApi merchantStock;
    private final OrderDraftWriter writer;
    private final OrderDraftWriter.PaymentSeedProvider payments;
    private final CheckoutCartApi checkoutCart;
    private final OrderRepository orders;
    private final PaymentQueryService paymentQueryService;
    private final ApplicationEventPublisher eventPublisher;

    public CheckoutTransactionService(CheckoutPreparationService preparation, MerchantStockApi merchantStock,
            OrderDraftWriter writer, OrderDraftWriter.PaymentSeedProvider payments, CheckoutCartApi checkoutCart,
            OrderRepository orders, PaymentQueryService paymentQueryService, ApplicationEventPublisher eventPublisher) {
        this.preparation = preparation;
        this.merchantStock = merchantStock;
        this.writer = writer;
        this.payments = payments;
        this.checkoutCart = checkoutCart;
        this.orders = orders;
        this.paymentQueryService = paymentQueryService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public CheckoutReceipt placeOrder(CheckoutQuote quote) {
        // 1. Phải có bản xem trước đã xác nhận.
        if (quote == null) {
            throw new CheckoutPreparationException("Vui lòng xem trước đơn hàng trước khi xác nhận.");
        }

        // 2. Đọc lại giỏ, sản phẩm, giá và tồn kho.
        PreparedCheckout fresh = preparation.prepare(quote.toForm(), quote.selectedItemIds());

        // 3. Kiểm tra dữ liệu có thay đổi hay không.
        quote.requireUnchanged(fresh);

        // 4. Chuẩn bị danh sách sản phẩm cần trừ kho.
        record StockLine(Long shopId, PreparedCheckout.Line line) {
        }

        List<StockLine> stockLines = fresh.shopOrders()
                .stream()
                .flatMap(shop -> shop.items()
                        .stream()
                        .map(line -> new StockLine(
                                shop.shopId(),
                                line
                        )))
                .sorted(
                        Comparator.comparing(
                                row -> row.line().productId()
                        )
                )
                .toList();

        // 5. Trừ kho bằng MerchantStockApi.
        for (StockLine stockLine : stockLines) {
            PreparedCheckout.Line line = stockLine.line();

            merchantStock.deductForCheckout(
                    stockLine.shopId(),
                    line.productId(),
                    line.quantity(),
                    line.unitPrice()
            );
        }

        // 6. Tạo Order, OrderItem, Payment và
        // OrderStatusHistory thông qua OrderDraftWriter.
        List<Long> orderIds = writer.save(fresh, payments);

        if (orderIds.size() != fresh.shopOrders().size()) {
            throw new IllegalStateException("Số đơn đã lưu không khớp số nhóm gian hàng.");
        }

        // 7. Chỉ xóa các cart item được mua.
        checkoutCart.removePurchasedItems(fresh.purchasedCartItems());

        // 8. Lấy thông tin Order và Payment đã lưu.
        List<CheckoutReceipt.ShopReceipt> summaries = new ArrayList<>();

        // ORDER 03: Tập hợp event cho mỗi order.
        List<OrderCreatedEvent> createdEvents = new ArrayList<>();

        for (int i = 0; i < orderIds.size(); i++) {
            Long orderId = orderIds.get(i);
            Order order = orders.findById(orderId).orElseThrow();
            PaymentDetails payment = paymentQueryService.getByOrderId(orderId);

            summaries.add(
                    new CheckoutReceipt.ShopReceipt(
                            fresh.shopOrders()
                                    .get(i)
                                    .shopNameSnapshot(),
                            order.getOrderCode(),
                            order.getTotalAmount(),
                            payment.method(),
                            payment.status(),
                            payment.transactionReference()
                    )
            );

            PreparedCheckout.ShopOrder shopOrder = fresh.shopOrders().get(i);
            if (shopOrder.shopOwnerId() == null || shopOrder.shopOwnerId() <= 0) {
                throw new IllegalStateException("Không xác định được chủ sở hữu gian hàng cho thông báo đơn hàng.");
            }

            // 9. ORDER 03 / NOTI 03: Mỗi shop/order có eventId riêng.
            createdEvents.add(new OrderCreatedEvent(
                    UUID.randomUUID(), order.getId(), order.getShopId(), shopOrder.shopOwnerId(),
                    order.getBuyerId(), order.getBuyerId(), order.getOrderCode(), order.getStatus().name(),
                    order.getCreatedAt()));
        }

        // 10. Publish trong transaction.
        for (OrderCreatedEvent event : createdEvents) {
            eventPublisher.publishEvent(event);
        }

        // 11. Transaction commit khi method
        // @Transactional kết thúc thành công.
        return new CheckoutReceipt(summaries, fresh.grandTotal());
    }
}
