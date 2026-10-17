package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;

import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;

import com.senvia.doangiuaky.ordering.api.OrderHistoryApi;
import com.senvia.doangiuaky.ordering.api.OrderHistoryEntry;

import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.OrderStatusHistory;

import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.OrderStatusHistoryRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class OrderHistoryReadService implements OrderHistoryApi {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final CurrentUserApi currentUserApi;
    private final MerchantApi merchantApi;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository histories;

    public OrderHistoryReadService(CurrentUserApi currentUserApi, MerchantApi merchantApi, OrderRepository orders, OrderStatusHistoryRepository histories) {
        this.currentUserApi = currentUserApi;
        this.merchantApi = merchantApi;
        this.orders = orders;
        this.histories = histories;
    }

    // Buyer chỉ xem lịch sử đơn hàng của chính mình.
    @Override
    @Transactional(readOnly = true)
    public List<OrderHistoryEntry> forCurrentBuyer(String orderCode) {
        UserSummary actor = requireActiveUser(UserRole.USER);
        Order order = findOrder(orderCode);

        if (!Objects.equals(order.getBuyerId(), actor.userId())) {
            throw notFound();
        }

        return loadHistory(order.getId());
    }

    /**
     * Seller chỉ xem lịch sử đơn thuộc shop mình sở hữu.
     * Shop LOCKED vẫn đọc được lịch sử đơn cũ
     * nếu tài khoản chủ shop còn ACTIVE.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrderHistoryEntry> forCurrentSeller(String orderCode) {
        UserSummary actor = requireActiveUser(UserRole.USER);
        Order order = findOrder(orderCode);

        MerchantShopSummary shop = merchantApi
                .findShopSummary(order.getShopId())
                .orElseThrow(OrderHistoryReadService::notFound);

        if (!shop.ownerActive() || !Objects.equals(shop.ownerId(), actor.userId())) {
            throw notFound();
        }

        return loadHistory(order.getId());
    }

    /**
     * Admin ACTIVE được xem lịch sử đơn hàng.
     */
    @Override
    @Transactional(readOnly = true)
    public List<OrderHistoryEntry> forCurrentAdmin(String orderCode) {
        requireActiveUser(UserRole.ADMIN);
        Order order = findOrder(orderCode);
        return loadHistory(order.getId());
    }

    /**
     * Lấy order bằng mã đơn.
     * Không trả order trực tiếp ra ngoài module.
     */
    private Order findOrder(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            throw notFound();
        }

        return orders.findByOrderCode(orderCode.trim()).orElseThrow(OrderHistoryReadService::notFound);
    }

    /**
     * ORDER 07:
     * Đọc lịch sử theo changedAt ASC, id ASC.
     *
     * id đóng vai trò tie-breaker nếu hai thay đổi
     * có cùng thời gian.
     */
    private List<OrderHistoryEntry> loadHistory(Long orderId) {
        return histories
                .findAllByOrderIdOrderByChangedAtAscIdAsc(orderId)
                .stream()
                .map(this::toEntry)
                .toList();
    }

    /**
     * Chuyển entity thành DTO an toàn.
     */
    private OrderHistoryEntry toEntry(OrderStatusHistory history) {
        OrderStatus oldStatus = history.getOldStatus();
        OrderStatus newStatus = history.getNewStatus();

        String title = oldStatus == null ? "Đơn hàng được tạo" : statusLabel(newStatus);

        return new OrderHistoryEntry(history.getId(), oldStatus == null ? null : oldStatus.name(), newStatus.name(), title, history.getChangedBy(), history.getNote(), history.getChangedAt(), DATE_FORMAT.format(history.getChangedAt()));
    }

    /**
     * Nhãn tiếng Việt để hiển thị trên timeline.
     */
    private String statusLabel(OrderStatus status) {
        return switch (status) {
            case PENDING -> "Chờ xác nhận";
            case CONFIRMED -> "Đơn hàng đã được xác nhận";
            case PREPARING -> "Đang chuẩn bị hàng";
            case SHIPPING -> "Đang giao hàng";
            case COMPLETED -> "Đơn hàng đã hoàn thành";
            case CANCELLED -> "Đơn hàng đã bị hủy";
        };
    }

    /**
     * Không lấy userId hoặc role từ request.
     */
    private UserSummary requireActiveUser(UserRole expectedRole) {
        UserSummary actor = currentUserApi.requireCurrentUser();

        if (actor.userId() == null || actor.accountStatus() != AccountStatus.ACTIVE || actor.role() != expectedRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không được phép xem lịch sử đơn hàng.");
        }

        return actor;
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
    }
}