package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantStockApi;
import com.senvia.doangiuaky.ordering.api.OrderCancellationApi;
import com.senvia.doangiuaky.ordering.api.OrderStatusChangedEvent;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderItem;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.OrderStatusHistory;
import com.senvia.doangiuaky.ordering.entity.Payment;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.OrderStatusHistoryRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class OrderCancellationService implements OrderCancellationApi {
    private final CurrentUserApi currentUserApi;
    private final MerchantApi merchantApi;
    private final MerchantStockApi stockApi;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final OrderStatusHistoryRepository histories;
    private final ApplicationEventPublisher events;

    public OrderCancellationService(CurrentUserApi currentUserApi, MerchantApi merchantApi, MerchantStockApi stockApi,
            OrderRepository orders, OrderItemRepository items, PaymentRepository payments,
            OrderStatusHistoryRepository histories, ApplicationEventPublisher events) {
        this.currentUserApi = currentUserApi;
        this.merchantApi = merchantApi;
        this.stockApi = stockApi;
        this.orders = orders;
        this.items = items;
        this.payments = payments;
        this.histories = histories;
        this.events = events;
    }

    @Override
    @Transactional
    public void cancelAsSeller(String orderCode, String reason) {
        cancel(orderCode, reason, ActorType.SELLER);
    }

    @Override
    @Transactional
    public void cancelAsBuyer(String orderCode, String reason) {
        cancel(orderCode, reason, ActorType.BUYER);
    }

    @Override
    @Transactional
    public void cancelAsAdmin(String orderCode, String reason) {
        cancel(orderCode, reason, ActorType.ADMIN);
    }

    private void cancel(String orderCode, String reason, ActorType actorType) {
        // 1. Lay tai khoan dang nhap thuc te.
        UserSummary actor = currentUserApi.requireCurrentUser();

        if (actor == null || actor.userId() == null || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không được xử lý đơn hàng.");
        }

        // 2. Kiem tra ma don.
        if (orderCode == null || orderCode.isBlank()) {
            throw notFound();
        }

        // 3. Khoa order trong transaction.
        Order order = orders
                .findByOrderCodeForUpdate(orderCode.strip())
                .orElseThrow(OrderCancellationService::notFound);

        // 4. Kiem tra quyen truoc khi xu ly du lieu huy.
        requireAuthorized(actor, actorType, order);

        // 5. Kiem tra ly do sau khi da xac nhan quyen.
        if (reason == null || reason.strip().isEmpty() || reason.strip().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do hủy phải có từ 1 đến 500 ký tự.");
        }

        String normalizedReason = reason.strip();

        // 6. Chỉ cho hủy 3 trạng thái hợp lệ.
        OrderStatus oldStatus = order.getStatus();

        if (oldStatus != OrderStatus.PENDING && oldStatus != OrderStatus.CONFIRMED && oldStatus != OrderStatus.PREPARING) {
            throw conflict("Đơn ở trạng thái " + oldStatus + " không thể hủy.");
        }

        // 7. Lấy payment thực tế.
        Payment payment = payments.findByOrderId(order.getId()).orElseThrow(() -> conflict("Đơn không có payment hợp lệ."));

        // 8. Đọc order items.
        List<OrderItem> orderedItems = items.findAllByOrderIdOrderByIdAsc(order.getId());

        if (orderedItems.isEmpty()) {
            throw conflict("Đơn không có sản phẩm để hoàn kho.");
        }

        // 9. Kiểm tra payment trước khi hoàn kho.
        try {
            payment.ensureCancellable();
        } catch (IllegalStateException exception) {
            throw conflict(exception.getMessage());
        }

        // 10. Hoàn kho đúng một lần theo từng orderItemId.
        // Sắp xếp productId để giảm nguy cơ deadlock.
        // MerchantStockApi tham gia transaction hiện tại.
        orderedItems.stream()
                .sorted(Comparator.comparing(OrderItem::getProductId).thenComparing(OrderItem::getId))
                .forEach(item ->
                        stockApi.restoreCancelledOrderItem(
                                item.getId(),
                                order.getShopId(),
                                item.getProductId(),
                                item.getQuantity()
                        )
                );

        // 11. Cập nhật payment và order.
        payment.settleForCancellation();
        order.changeStatus(OrderStatus.CANCELLED);

        payments.save(payment);
        orders.save(order);

        // 12. Lưu lịch sử với actor và lý do hủy.
        histories.save(new OrderStatusHistory(order.getId(), oldStatus, OrderStatus.CANCELLED, actor.userId(), "Hủy đơn. Lý do: " + normalizedReason));

        // 13. Publish event trong transaction.
        // Listener AFTER_COMMIT chỉ chạy sau commit thành công.
        events.publishEvent(new OrderStatusChangedEvent(UUID.randomUUID(), order.getId(), order.getShopId(), order.getBuyerId(), actor.userId(), order.getOrderCode(), oldStatus.name(), OrderStatus.CANCELLED.name(), Instant.now()));
    }

    private void requireAuthorized(UserSummary actor, ActorType type, Order order) {
        boolean allowed = switch (type) {
            case BUYER -> actor.role() == UserRole.USER && Objects.equals(order.getBuyerId(), actor.userId());

            case ADMIN -> actor.role() == UserRole.ADMIN;

            case SELLER -> actor.role() == UserRole.USER && merchantApi
                        .findShopSummary(order.getShopId())
                        .filter(shop -> Objects.equals(shop.ownerId(), actor.userId()) && shop.ownerActive())
                        .isPresent()
                    && merchantApi
                        .findShopOrderCapabilities(order.getShopId())
                        .map(capabilities ->capabilities.canHandleExistingOrders())
                        .orElse(false);
        };

        if (!allowed) {
            // Không tiết lộ sự tồn tại của đơn shop khác.
            throw notFound();
        }
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
    }

    private static ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }

    private enum ActorType {
        BUYER,
        SELLER,
        ADMIN
    }
}
