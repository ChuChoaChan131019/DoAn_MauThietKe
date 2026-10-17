package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.ordering.dto.SellerOrderView;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderItem;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import com.senvia.doangiuaky.ordering.dto.SellerOrderPage;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Locale;

@Service
public class SellerOrderReadService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final int PAGE_SIZE = 10;

    private final CurrentUserApi currentUserApi;
    private final MerchantApi merchantApi;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentQueryService payments;

    public SellerOrderReadService(CurrentUserApi currentUserApi, MerchantApi merchantApi, OrderRepository orders, OrderItemRepository items, PaymentQueryService payments) {
        this.currentUserApi = currentUserApi;
        this.merchantApi = merchantApi;
        this.orders = orders;
        this.items = items;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public SellerOrderView getOwnOrder(String orderCode) {
        UserSummary actor = requireActiveSeller();

        if (orderCode == null || orderCode.isBlank()) {
            throw notFound();
        }

        Order order = orders.findByOrderCode(orderCode).orElseThrow(SellerOrderReadService::notFound);

        MerchantShopSummary shop = merchantApi.findShopSummary(order.getShopId()).orElseThrow(SellerOrderReadService::notFound);

        if (!Objects.equals(shop.ownerId(), actor.userId()) || !shop.ownerActive()) {
            throw notFound();
        }

        // Chỉ đọc thông tin payment sau khi kiểm tra quyền.
        return toView(order);
    }

    @Transactional(readOnly = true)
    public List<SellerOrderView> listOwnOrders(String statusFilter) {
        UserSummary actor = requireActiveSeller();

        List<Long> ownedShopIds = findOwnedShopIds(actor.userId());

        if (ownedShopIds.isEmpty()) {
            return List.of();
        }

        return orders.findAllByShopIdInOrderByCreatedAtDesc(ownedShopIds)
                .stream()
                .filter(order -> statusFilter == null
                        || statusFilter.isBlank()
                        || order.getStatus().name().equals(statusFilter))
                .map(this::toView)
                .toList();
    }

    // ORDER 02 - Danh sách đơn của chủ shop có phân trang.
    @Transactional(readOnly = true)
    public SellerOrderPage pageOwnOrders(String statusFilter, int requestedPage) {
        UserSummary actor = requireActiveSeller();

        if (requestedPage < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số trang không hợp lệ.");
        }

        OrderStatus status = parseStatusFilter(statusFilter);

        List<Long> ownedShopIds = findOwnedShopIds(actor.userId());

        if (ownedShopIds.isEmpty()) {
            return SellerOrderPage.empty();
        }

        Pageable pageable = PageRequest.of(
                requestedPage - 1,
                PAGE_SIZE,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));

        Page<Order> orderPage = findOrderPage(ownedShopIds, status, pageable);

        // Trang vượt giới hạn thì đưa về trang cuối.
        if (orderPage.getTotalPages() > 0 && requestedPage > orderPage.getTotalPages()) {
            pageable = PageRequest.of(orderPage.getTotalPages() - 1, PAGE_SIZE, pageable.getSort());
            orderPage = findOrderPage(ownedShopIds, status, pageable);
        }

        long pendingCount = orders.countByShopIdInAndStatus(ownedShopIds, OrderStatus.PENDING);

        // Chỉ đọc chi tiết sau khi database đã lọc shop.
        List<SellerOrderView> views = orderPage
                .getContent()
                .stream()
                .map(this::toView)
                .toList();

        int currentPage = orderPage.getTotalPages() == 0 ? 1 : orderPage.getNumber() + 1;
        return new SellerOrderPage(views, currentPage, Math.max(1, orderPage.getTotalPages()),
                orderPage.getTotalElements(), pendingCount);
    }

    // Truy vấn danh sách đơn theo shop và trạng thái.
    private Page<Order> findOrderPage(List<Long> shopIds, OrderStatus status, Pageable pageable) {
        if (status == null) {
            return orders.findByShopIdIn(shopIds, pageable);
        }

        return orders.findByShopIdInAndStatus(shopIds, status, pageable);
    }

    // Xác định các shop do user hiện tại sở hữu.
    private List<Long> findOwnedShopIds(Long ownerId) {
        return orders.findDistinctShopIds()
                .stream()
                .filter(shopId -> merchantApi.findShopSummary(shopId)
                        .filter(shop -> shop.ownerActive() && Objects.equals(
                                shop.ownerId(),
                                ownerId))
                        .isPresent())
                .toList();
    }

    // Kiểm tra giá trị bộ lọc trạng thái.
    private OrderStatus parseStatusFilter(String statusFilter) {
        if (statusFilter == null || statusFilter.isBlank()) {
            return null;
        }

        try {
            return OrderStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái đơn hàng không hợp lệ.");
        }
    }

    private UserSummary requireActiveSeller() {
        UserSummary actor = currentUserApi.requireCurrentUser();

        if (actor.userId() == null
                || actor.role() != UserRole.USER
                || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        return actor;
    }

    private SellerOrderView toView(Order order) {
        List<SellerOrderView.Item> lines = items
                .findAllByOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(this::toItem)
                .toList();

        return new SellerOrderView(
                order.getOrderCode(),
                order.getCreatedAt() == null ? "" : DATE_FORMAT.format(order.getCreatedAt()),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getShippingAddress(),
                order.getStatus().name(),
                order.getTotalAmount(),
                payments.getByOrderId(order.getId()),
                lines
        );
    }

    private SellerOrderView.Item toItem(OrderItem item) {
        return new SellerOrderView.Item(
                item.getProductId(),
                item.getProductNameSnapshot(),
                item.getUnitPrice(),
                item.getQuantity(),
                item.getSubtotal()
        );
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
    }
}
