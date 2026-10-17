package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;

import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.merchant.api.ShopOrderCapabilities;

import com.senvia.doangiuaky.ordering.api.OrderStatusChangedEvent;

import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.OrderStatusHistory;
import com.senvia.doangiuaky.ordering.entity.Payment;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;

import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.OrderStatusHistoryRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class SellerOrderStatusService {
    private final CurrentUserApi currentUserApi;
    private final MerchantApi merchantApi;
    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final OrderStatusHistoryRepository histories;
    private final ApplicationEventPublisher eventPublisher;

    public SellerOrderStatusService(CurrentUserApi currentUserApi, MerchantApi merchantApi, OrderRepository orders, PaymentRepository payments, OrderStatusHistoryRepository histories, ApplicationEventPublisher eventPublisher) {
        this.currentUserApi = currentUserApi;
        this.merchantApi = merchantApi;
        this.orders = orders;
        this.payments = payments;
        this.histories = histories;
        this.eventPublisher = eventPublisher;
    }

    // ORDER 04: Chủ shop chuyển trạng thái đơn hàng theo luồng hợp lệ.
    // Kiểm tra quyền, trạng thái đơn, payment và history
    // trong cùng một transaction.
    @Transactional
    public void changeStatus(String orderCode, OrderStatus requestedStatus) {
        // 1. Xác thực người thực hiện.
        UserSummary actor = currentUserApi.requireCurrentUser();

        if (actor.userId() == null || actor.role() != UserRole.USER || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không được phép xử lý đơn hàng.");
        }

        // 2. Kiểm tra dữ liệu đầu vào.
        if (orderCode == null || orderCode.isBlank()) {
            throw notFound();
        }

        if (requestedStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái yêu cầu không hợp lệ.");
        }

        // 3 + 4. Tìm và khóa đơn ngay trong một truy vấn.
        Order order = orders
                .findByOrderCodeForUpdate(orderCode)
                .orElseThrow(SellerOrderStatusService::notFound);

        // 5. Kiểm tra chủ sở hữu shop tại backend.
        MerchantShopSummary shop = merchantApi.findShopSummary(order.getShopId()).orElseThrow(SellerOrderStatusService::notFound);

        if (!shop.ownerActive() || !Objects.equals(shop.ownerId(), actor.userId())) {
            // Không tiết lộ đơn hàng của shop khác.
            throw notFound();
        }

        // 6. Kiểm tra quyền xử lý đơn cũ.
        // APPROVED và LOCKED có thể xử lý đơn tồn tại
        // nếu Merchant API cho phép.
        ShopOrderCapabilities capabilities = merchantApi.findShopOrderCapabilities(order.getShopId()).orElseThrow(SellerOrderStatusService::notFound);

        if (!capabilities.canHandleExistingOrders()) {
            throw conflict("Gian hàng không được phép xử lý đơn hàng này.");
        }

        // 7. Kiểm tra trạng thái tiếp theo.
        OrderStatus oldStatus = order.getStatus();
        OrderStatus expectedStatus = nextStatus(oldStatus);

        if (expectedStatus == null) {
            throw conflict("Đơn hàng đã kết thúc hoặc không thể chuyển tiếp.");
        }

        if (requestedStatus != expectedStatus) {
            throw conflict("Chuyển trạng thái không hợp lệ. " + "Trạng thái hiện tại: " + oldStatus + ". Trạng thái tiếp theo phải là: " + expectedStatus);
        }

        // 8. Lấy payment thật từ bảng payments.
        Payment payment = payments.findByOrderId(order.getId()).orElseThrow(() -> conflict("Đơn hàng chưa có bản ghi thanh toán."));

        // 9. Kiểm tra điều kiện thanh toán.
        validatePayment(payment);

        // 10. COD chỉ được đánh dấu PAID khi hoàn thành đơn.
        if (requestedStatus == OrderStatus.COMPLETED && payment.getMethod() == PaymentMethod.COD) {
            payment.markCodPaid();
            payments.save(payment);
        }

        // 11. Đổi trạng thái đơn.
        order.changeStatus(requestedStatus);
        orders.save(order);

        // 12. Ghi lịch sử trong cùng transaction.
        histories.save(new OrderStatusHistory(order.getId(), oldStatus, requestedStatus, actor.userId(), "Chủ shop cập nhật trạng thái đơn hàng."));

        // 13. Công bố event trong transaction.
        // Listener AFTER_COMMIT chỉ chạy nếu commit thành công.
        eventPublisher.publishEvent(
                new OrderStatusChangedEvent(UUID.randomUUID(), order.getId(), order.getShopId(), order.getBuyerId(), actor.userId(), order.getOrderCode(), oldStatus.name(), requestedStatus.name(), Instant.now())
        );
    }

    // Xác định bước tiếp theo duy nhất.
    private OrderStatus nextStatus(OrderStatus current) {
        return switch (current) {
            case PENDING -> OrderStatus.CONFIRMED;
            case CONFIRMED -> OrderStatus.PREPARING;
            case PREPARING -> OrderStatus.SHIPPING;
            case SHIPPING -> OrderStatus.COMPLETED;
            case COMPLETED, CANCELLED -> null;
        };
    }

    // Kiểm tra payment trước mỗi lần chuyển trạng thái.
    private void validatePayment(Payment payment) {
        // Chuyển khoản phải PAID trước khi xác nhận đơn.
        if (payment.getMethod() == PaymentMethod.BANK_TRANSFER) {
            if (payment.getStatus() != PaymentStatus.PAID) {
                throw conflict("Đơn chuyển khoản phải được xác nhận " + "đã thanh toán trước khi xử lý.");
            }
            return;
        }

        // COD có thể xử lý khi đang chờ thu tiền.
        if (payment.getMethod() == PaymentMethod.COD) {
            if (payment.getStatus() != PaymentStatus.COD_PENDING) {
                throw conflict("Thanh toán COD không ở trạng thái hợp lệ.");
            }
            return;
        }

        throw conflict("Phương thức thanh toán không được hỗ trợ.");
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}