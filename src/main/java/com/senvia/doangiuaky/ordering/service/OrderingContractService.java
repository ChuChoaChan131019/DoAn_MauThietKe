package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.api.*;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderItem;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.Payment;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class OrderingContractService implements BuyerOrderApi, AdminOrderApi, ReviewOrderApi, OrderingStatisticsApi {
    private final CurrentUserApi currentUserApi;
    private final MerchantApi merchantApi;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentRepository payments;
    private final OrderHistoryApi histories;

    public OrderingContractService(CurrentUserApi currentUserApi, MerchantApi merchantApi, OrderRepository orders, OrderItemRepository items, PaymentRepository payments, OrderHistoryApi histories) {
        this.currentUserApi = currentUserApi;
        this.merchantApi = merchantApi;
        this.orders = orders;
        this.items = items;
        this.payments = payments;
        this.histories = histories;
    }

    // ==========================================
    // BUYER CONTRACT
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public OrderPage listOwnOrders(String status, int page, int size) {
        UserSummary buyer = requireActive(UserRole.USER);

        Pageable paging = paging(page, size);
        OrderStatus filter = parseStatus(status);

        Page<Order> result = filter == null
                ? orders.findByBuyerId(buyer.userId(), paging)
                : orders.findByBuyerIdAndStatus(buyer.userId(), filter, paging);

        return toPage(result);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetails getOwnOrder(String orderCode) {
        UserSummary buyer = requireActive(UserRole.USER);

        Order order = findOrder(orderCode);

        if (!Objects.equals(order.getBuyerId(), buyer.userId())) {
            throw notFound();
        }

        return toDetails(order, histories.forCurrentBuyer(order.getOrderCode()));
    }

    // ==========================================
    // ADMIN CONTRACT
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public OrderPage listOrders(String status, Long buyerId, Long shopId, String orderCodeQuery, int page, int size) {
        requireActive(UserRole.ADMIN);

        Pageable paging = paging(page, size);
        OrderStatus statusFilter = parseStatus(status);

        requirePositiveOrNull(buyerId, "buyerId");
        requirePositiveOrNull(shopId, "shopId");

        Specification<Order> spec = (root, query, cb) -> cb.conjunction();

        if (statusFilter != null) {
            spec = spec.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("status"),
                                    statusFilter
                            )
            );
        }

        if (buyerId != null) {
            spec = spec.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("buyerId"),
                                    buyerId
                            )
            );
        }

        if (shopId != null) {
            spec = spec.and(
                    (root, query, cb) ->
                            cb.equal(
                                    root.get("shopId"),
                                    shopId
                            )
            );
        }

        if (orderCodeQuery != null && !orderCodeQuery.isBlank()) {
            String term = orderCodeQuery.strip()
                    .toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\")
                    .replace("%", "\\%")
                    .replace("_", "\\_");

            spec = spec.and(
                    (root, query, cb) ->
                            cb.like(
                                    cb.lower(root.get("orderCode")),
                                    "%" + term + "%",
                                    '\\'
                            )
            );
        }

        return toPage(orders.findAll(spec, paging));
    }

    @Override
    @Transactional(readOnly = true)
    public OrderDetails getOrder(String orderCode) {
        requireActive(UserRole.ADMIN);

        Order order = findOrder(orderCode);

        return toDetails(order, histories.forCurrentAdmin(order.getOrderCode()));
    }

    // ==========================================
    // REVIEW CONTRACT
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public ReviewableOrderItem requireReviewableItem(Long orderItemId, Long productId) {
        UserSummary buyer = requireActive(UserRole.USER);

        if (orderItemId == null || orderItemId <= 0 || productId == null || productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "orderItemId/productId không hợp lệ.");
        }

        OrderItem item = items.findById(orderItemId).orElseThrow(OrderingContractService::notFound);

        // Sản phẩm phải đúng với order item.
        if (!Objects.equals(item.getProductId(), productId)) {
            throw notFound();
        }

        Order order = orders.findById(item.getOrderId()).orElseThrow(OrderingContractService::notFound);

        // Buyer không được đánh giá đơn của người khác.
        if (!Objects.equals(order.getBuyerId(), buyer.userId())) {
            throw notFound();
        }

        // Chỉ đánh giá đơn hoàn thành.
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ được đánh giá sản phẩm thuộc đơn đã hoàn thành.");
        }

        return new ReviewableOrderItem(item.getId(), order.getId(), order.getOrderCode(), order.getBuyerId(), order.getShopId(), item.getProductId());
    }

    // ==========================================
    // STATISTICS CONTRACT
    // ==========================================

    @Override
    @Transactional(readOnly = true)
    public ShopOrderStatistics forOwnShop(Long shopId) {
        UserSummary seller = requireActive(UserRole.USER);

        if (shopId == null || shopId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shopId không hợp lệ.");
        }

        var shop = merchantApi.findShopSummary(shopId).orElseThrow(OrderingContractService::notFound);

        // Không cho xem thống kê shop của người khác.
        if (!Objects.equals(shop.ownerId(), seller.userId()) || !shop.ownerActive()) {
            throw notFound();
        }

        Map<String, Long> counts = new LinkedHashMap<>();

        // Luôn trả đầy đủ các trạng thái,
        // kể cả trạng thái không có đơn.
        for (OrderStatus status : OrderStatus.values()) {
            counts.put(status.name(), 0L);
        }

        long total = 0;

        for (Object[] entry : orders.countOrdersByShopAndStatus(shopId)) {
            String status = ((OrderStatus) entry[0]).name();
            long count = ((Number) entry[1]).longValue();

            counts.put(status, count);
            total += count;
        }

        // Chỉ tính doanh thu COMPLETED.
        BigDecimal revenue = orders.sumAmountByShopAndStatus(shopId, OrderStatus.COMPLETED);

        return new ShopOrderStatistics(shopId, total, counts, revenue == null ? BigDecimal.ZERO : revenue);
    }

    // ==========================================
    // AUTHORIZATION
    // ==========================================

    private UserSummary requireActive(UserRole expectedRole) {
        UserSummary actor = currentUserApi.requireCurrentUser();

        if (actor.userId() == null || actor.accountStatus() != AccountStatus.ACTIVE || actor.role() != expectedRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không có quyền truy cập đơn hàng.");
        }

        return actor;
    }

    // ==========================================
    // VALIDATION
    // ==========================================

    private static Pageable paging(int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang phải từ 1 và kích thước trang phải từ 1 đến 100.");
        }

        return PageRequest.of(page - 1, size,
                Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id")
                )
        );
    }

    private static OrderStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return OrderStatus.valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái không hợp lệ.");
        }
    }

    private static void requirePositiveOrNull(Long value, String field) {
        if (value != null && value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " không hợp lệ.");
        }
    }

    // ==========================================
    // ORDER DATA MAPPING
    // ==========================================

    private Order findOrder(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            throw notFound();
        }

        return orders.findByOrderCode(orderCode.strip()).orElseThrow(OrderingContractService::notFound);
    }

    private static OrderPage toPage(Page<Order> page) {
        return new OrderPage(
                page.getContent().stream()
                        .map(OrderingContractService::toSummary)
                        .toList(),
                page.getNumber() + 1,
                Math.max(1, page.getTotalPages()),
                page.getTotalElements()
        );
    }

    private static OrderSummary toSummary(Order order) {
        return new OrderSummary(order.getOrderCode(), order.getBuyerId(), order.getShopId(), order.getStatus().name(), order.getTotalAmount(),order.getCreatedAt());
    }

    private OrderDetails toDetails(Order order, List<OrderHistoryEntry> history) {
        Payment payment = payments.findByOrderId(order.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Đơn hàng thiếu thông tin thanh toán."));

        List<OrderDetails.Item> lines = items
                .findAllByOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(item -> new OrderDetails.Item(
                        item.getId(),
                        item.getProductId(),
                        item.getProductNameSnapshot(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.getSubtotal()
                ))
                .toList();

        var paymentInfo = new OrderDetails.PaymentInfo(
                payment.getMethod().name(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getTransactionReference(),
                payment.getPaidAt(),
                payment.getRefundedAt()
        );

        return new OrderDetails(
                toSummary(order),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getShippingAddress(),
                order.getNote(),
                paymentInfo,
                lines,
                history
        );
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
    }
}
