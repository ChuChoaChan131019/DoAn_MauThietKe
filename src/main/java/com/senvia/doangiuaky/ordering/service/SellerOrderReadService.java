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

@Service
public class SellerOrderReadService {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

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
        // Resolve owned shop IDs using Merchant's existing public contract before
        // loading any order, recipient, item or payment details.
        List<Long> ownedShopIds = orders.findDistinctShopIds()
                .stream()
                .filter(shopId -> merchantApi.findShopSummary(shopId)
                        .filter(shop -> shop.ownerActive()
                                && Objects.equals(shop.ownerId(), actor.userId()))
                        .isPresent())
                .toList();

        if (ownedShopIds.isEmpty()) {
            return List.of();
        }

        return orders.findAllByShopIdInOrderByCreatedAtDesc(ownedShopIds)
                .stream()
                .filter(order ->
                        statusFilter == null
                        || statusFilter.isBlank()
                        || order.getStatus().name().equals(statusFilter))
                .map(this::toView)
                .toList();
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
