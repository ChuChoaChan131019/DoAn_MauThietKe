package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;

import com.senvia.doangiuaky.ordering.api.OrderDetailEntry;
import com.senvia.doangiuaky.ordering.api.OrderListEntry;
import com.senvia.doangiuaky.ordering.api.OrderQueryApi;
import com.senvia.doangiuaky.ordering.dto.PaymentDetails;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderItem;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.OrderItemRepository;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

@Service
public class OrderDetailReadService implements OrderQueryApi {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final CurrentUserApi currentUserApi;
    private final MerchantApi merchantApi;
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final PaymentQueryService payments;

    public OrderDetailReadService(CurrentUserApi currentUserApi, MerchantApi merchantApi, OrderRepository orders, OrderItemRepository items, PaymentQueryService payments) {
        this.currentUserApi = currentUserApi;
        this.merchantApi = merchantApi;
        this.orders = orders;
        this.items = items;
        this.payments = payments;
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderListEntry> listForCurrentBuyer() {
        UserSummary actor = requireActiveUser(UserRole.USER);
        return orders.findAllByBuyerIdOrderByCreatedAtDescIdDesc(actor.userId())
                .stream()
                .map(this::toListEntry)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrderListEntry> listForCurrentAdmin() {
        requireActiveUser(UserRole.ADMIN);
        return orders.findAll(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
                .stream()
                .map(this::toListEntry)
                .toList();
    }

    /**
     * ORDER 09:
     * Buyer chỉ được xem chi tiết đơn hàng của mình.
     */
    @Transactional(readOnly = true)
    public OrderDetailEntry getBuyerOrder(String orderCode) {
        // 1. Kiểm tra tài khoản trước khi truy vấn.
        UserSummary actor = requireActiveUser(UserRole.USER);

        // 2. Tìm đơn bằng mã đơn.
        Order order = findOrder(orderCode);

        // 3. Kiểm tra buyer sở hữu order.
        if (!Objects.equals(order.getBuyerId(), actor.userId())) {
            // Không tiết lộ đơn hàng của người khác.
            throw notFound();
        }

        // 4. Chỉ đọc sản phẩm/payment sau khi có quyền.
        return toDetail(order);
    }

    /**
     * ORDER 09:
     * Chỉ Admin ACTIVE được xem chi tiết đơn giám sát.
     */
    @Transactional(readOnly = true)
    public OrderDetailEntry getAdminOrder(String orderCode) {
        // 1. Kiểm tra vai trò và trạng thái tài khoản.
        requireActiveUser(UserRole.ADMIN);

        // 2. Admin được đọc đơn phục vụ quản trị.
        Order order = findOrder(orderCode);

        // 3. Trả về DTO, không trả Entity.
        return toDetail(order);
    }

    private Order findOrder(String orderCode) {
        if (orderCode == null || orderCode.isBlank()) {
            throw notFound();
        }

        return orders.findByOrderCode(orderCode.strip()).orElseThrow(OrderDetailReadService::notFound);
    }

    /**
     * Không sử dụng userId/role do request gửi lên.
     */
    private UserSummary requireActiveUser(UserRole requiredRole) {
        UserSummary actor = currentUserApi.requireCurrentUser();

        if (actor == null || actor.userId() == null || actor.role() != requiredRole || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không được phép xem đơn hàng.");
        }

        return actor;
    }

    /**
     * Chỉ gọi sau khi quyền trên order được xác thực.
     */
    private OrderListEntry toListEntry(Order order) {
        String shopName = merchantApi.findShopSummary(order.getShopId())
                .map(shop -> shop.shopName())
                .orElse("Gian hàng #" + order.getShopId());
        return new OrderListEntry(
                order.getOrderCode(),
                formatDate(order),
                order.getShopId(),
                shopName,
                order.getBuyerId(),
                order.getStatus().name(),
                order.getTotalAmount());
    }

    private OrderDetailEntry toDetail(Order order) {
        List<OrderDetailEntry.Item> lines = items
                .findAllByOrderIdOrderByIdAsc(order.getId())
                .stream()
                .map(this::toItem)
                .toList();
        PaymentDetails payment = payments.getByOrderId(order.getId());

        return new OrderDetailEntry(
                order.getOrderCode(),
                formatDate(order),
                order.getShopId(),
                order.getBuyerId(),
                order.getReceiverName(),
                order.getReceiverPhone(),
                order.getShippingAddress(),
                order.getStatus().name(),
                order.getTotalAmount(),
                new OrderDetailEntry.Payment(
                        payment.method().name(),
                        payment.status().name(),
                        payment.amount(),
                        payment.transactionReference(),
                        payment.paidAt(),
                        payment.refundedAt()),
                lines
        );
    }

    private String formatDate(Order order) {
        return order.getCreatedAt() == null ? "" : DATE_FORMAT.format(order.getCreatedAt());
    }

    private OrderDetailEntry.Item toItem(OrderItem item) {
        return new OrderDetailEntry.Item(
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
