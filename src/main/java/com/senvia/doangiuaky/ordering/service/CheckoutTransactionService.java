package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.merchant.api.MerchantStockApi;

import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.Payment;

import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import com.senvia.doangiuaky.shopping.api.CheckoutCartApi;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CheckoutTransactionService {
    private final CheckoutPreparationService preparation;
    private final MerchantStockApi merchantStock;
    private final OrderDraftWriter writer;
    private final OrderDraftWriter.PaymentSeedProvider payments;
    private final CheckoutCartApi checkoutCart;
    private final OrderRepository orders;
    private final PaymentRepository paymentRepository;

    public CheckoutTransactionService(CheckoutPreparationService preparation, MerchantStockApi merchantStock,
            OrderDraftWriter writer, OrderDraftWriter.PaymentSeedProvider payments, CheckoutCartApi checkoutCart,
            OrderRepository orders, PaymentRepository paymentRepository) {
        this.preparation = preparation;
        this.merchantStock = merchantStock;
        this.writer = writer;
        this.payments = payments;
        this.checkoutCart = checkoutCart;
        this.orders = orders;
        this.paymentRepository = paymentRepository;
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

        // 6. Tạo Order, OrderItem và Payment.
        // PaymentInitializationService chính là PAY 04.
        List<Long> orderIds = writer.save(fresh, payments);

        // 7. Chỉ xóa các cart item được mua.
        checkoutCart.removePurchasedItems(fresh.purchasedCartItems());

        // 8. Lấy thông tin Order và Payment đã lưu.
        List<CheckoutReceipt.ShopReceipt> summaries = new ArrayList<>();

        for (int i = 0; i < orderIds.size(); i++) {
            Long orderId = orderIds.get(i);
            Order order = orders.findById(orderId).orElseThrow();
            Payment payment = paymentRepository.findByOrderId(orderId).orElseThrow();

            summaries.add(
                    new CheckoutReceipt.ShopReceipt(
                            fresh.shopOrders()
                                    .get(i)
                                    .shopNameSnapshot(),
                            order.getOrderCode(),
                            order.getTotalAmount(),
                            payment.getMethod(),
                            payment.getStatus(),
                            payment.getTransactionReference()
                    )
            );
        }

        // 9. Trả kết quả sau khi chuẩn bị xong.
        return new CheckoutReceipt(summaries,fresh.grandTotal());
    }
}