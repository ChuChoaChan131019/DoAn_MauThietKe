package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.ordering.api.OrderStatusChangedEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class OrderStatusChangedNotificationListener {

    static final String STATUS_CHANGED_TITLE = "Đơn hàng đã cập nhật trạng thái";
    static final String SELLER_CANCELLED_TITLE = "Đơn hàng đã bị hủy";
    static final String BUYER_CANCELLED_TITLE = "Người mua đã hủy đơn hàng";
    static final String ADMIN_CANCELLED_BUYER_TITLE = "Đơn hàng đã bị hủy";
    static final String ADMIN_CANCELLED_OWNER_TITLE = "Đơn hàng đã bị hủy bởi quản trị viên";

    private static final String UNIQUE_CONSTRAINT = "uk_notifications_event_receiver";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(OrderStatusChangedNotificationListener.class);

    private final MerchantApi merchantApi;
    private final IdentityApi identityApi;
    private final NotificationWriter notificationWriter;

    public OrderStatusChangedNotificationListener(MerchantApi merchantApi,
                                                  IdentityApi identityApi,
                                                  NotificationWriter notificationWriter) {
        this.merchantApi = merchantApi;
        this.identityApi = identityApi;
        this.notificationWriter = notificationWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (!isValid(event)) {
            LOGGER.warn("Ignoring invalid OrderStatusChangedEvent");
            return;
        }

        try {
            OrderStatus oldStatus = parseStatus(event.oldStatus()).orElse(null);
            OrderStatus newStatus = parseStatus(event.newStatus()).orElse(null);
            if (oldStatus == null || newStatus == null || !isValidTransition(oldStatus, newStatus)) {
                LOGGER.warn("Ignoring invalid order status transition in OrderStatusChangedEvent");
                return;
            }

            Optional<MerchantShopSummary> shopResult = merchantApi.findShopSummary(event.shopId());
            if (shopResult == null || shopResult.isEmpty()) {
                LOGGER.warn("Ignoring order status event because shop owner cannot be resolved, eventId={}",
                        event.eventId());
                return;
            }

            MerchantShopSummary shop = shopResult.get();
            Long shopOwnerId = shop.ownerId();
            if (!isPositive(shopOwnerId) || Objects.equals(shopOwnerId, event.buyerId())) {
                LOGGER.warn("Ignoring order status event because receiver ownership is invalid, eventId={}",
                        event.eventId());
                return;
            }

            Optional<UserSummary> actorResult = identityApi.findUser(event.actorId());
            if (actorResult == null || actorResult.isEmpty()) {
                LOGGER.warn("Ignoring order status event because actor cannot be resolved, eventId={}",
                        event.eventId());
                return;
            }

            UserSummary actor = actorResult.get();
            List<NotificationCommand> notifications = resolveNotifications(
                    event, oldStatus, newStatus, shopOwnerId, actor);
            notifications.forEach(notification -> createNotification(event, notification));
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to process order status notification eventId={}, orderId={}",
                    event.eventId(), event.orderId(), exception);
        }
    }

    private List<NotificationCommand> resolveNotifications(OrderStatusChangedEvent event,
                                                            OrderStatus oldStatus,
                                                            OrderStatus newStatus,
                                                            Long shopOwnerId,
                                                            UserSummary actor) {
        String orderCode = event.orderCode().trim();

        if (Objects.equals(event.actorId(), shopOwnerId)) {
            if (actor.role() != UserRole.USER) {
                return List.of();
            }
            if (newStatus == OrderStatus.CANCELLED) {
                return List.of(new NotificationCommand(
                        event.buyerId(),
                        SELLER_CANCELLED_TITLE,
                        "Đơn hàng " + orderCode + " đã bị gian hàng hủy."));
            }
            return newStatus.isSellerTransitionFrom(oldStatus)
                    ? List.of(new NotificationCommand(
                    event.buyerId(),
                    STATUS_CHANGED_TITLE,
                    "Đơn hàng " + orderCode + " đã chuyển từ "
                            + oldStatus.label + " sang " + newStatus.label + "."))
                    : List.of();
        }

        if (Objects.equals(event.actorId(), event.buyerId())) {
            if (actor.role() != UserRole.USER || newStatus != OrderStatus.CANCELLED) {
                return List.of();
            }
            return List.of(new NotificationCommand(
                    shopOwnerId,
                    BUYER_CANCELLED_TITLE,
                    "Người mua đã hủy đơn hàng " + orderCode + "."));
        }

        if (actor.role() != UserRole.ADMIN || newStatus != OrderStatus.CANCELLED) {
            return List.of();
        }
        return List.of(
                new NotificationCommand(
                        event.buyerId(),
                        ADMIN_CANCELLED_BUYER_TITLE,
                        "Đơn hàng " + orderCode + " đã bị quản trị viên hủy."),
                new NotificationCommand(
                        shopOwnerId,
                        ADMIN_CANCELLED_OWNER_TITLE,
                        "Quản trị viên đã hủy đơn hàng " + orderCode + "."));
    }

    private void createNotification(OrderStatusChangedEvent event, NotificationCommand notification) {
        try {
            notificationWriter.create(
                    event.eventId(),
                    notification.receiverId(),
                    NotificationType.ORDER_STATUS_CHANGED,
                    notification.title(),
                    notification.content(),
                    event.orderId());
        } catch (DataIntegrityViolationException exception) {
            if (isReplay(exception)) {
                LOGGER.debug("Order status notification already exists for eventId={}, receiverId={}",
                        event.eventId(), notification.receiverId());
            } else {
                LOGGER.error("Unable to create order status notification for eventId={}, orderId={}, receiverId={}",
                        event.eventId(), event.orderId(), notification.receiverId(), exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to create order status notification for eventId={}, orderId={}, receiverId={}",
                    event.eventId(), event.orderId(), notification.receiverId(), exception);
        }
    }

    private static boolean isValid(OrderStatusChangedEvent event) {
        return event != null
                && event.eventId() != null
                && isPositive(event.orderId())
                && isPositive(event.shopId())
                && isPositive(event.buyerId())
                && isPositive(event.actorId())
                && StringUtils.hasText(event.orderCode())
                && StringUtils.hasText(event.oldStatus())
                && StringUtils.hasText(event.newStatus())
                && event.occurredAt() != null;
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private static Optional<OrderStatus> parseStatus(String value) {
        try {
            return Optional.of(OrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException | NullPointerException exception) {
            return Optional.empty();
        }
    }

    private static boolean isValidTransition(OrderStatus oldStatus, OrderStatus newStatus) {
        return newStatus == OrderStatus.CANCELLED
                ? oldStatus == OrderStatus.PENDING
                || oldStatus == OrderStatus.CONFIRMED
                || oldStatus == OrderStatus.PREPARING
                : newStatus.isSellerTransitionFrom(oldStatus);
    }

    private static boolean isReplay(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && constraintViolation.getConstraintName() != null
                    && constraintViolation.getConstraintName().toLowerCase(Locale.ROOT)
                    .contains(UNIQUE_CONSTRAINT)) {
                return true;
            }
            cause = cause.getCause();
        }
        return exception.getMessage() != null
                && exception.getMessage().toLowerCase(Locale.ROOT).contains(UNIQUE_CONSTRAINT);
    }

    private enum OrderStatus {
        PENDING("Chờ xử lý"),
        CONFIRMED("Đã xác nhận"),
        PREPARING("Đang chuẩn bị"),
        SHIPPING("Đang giao hàng"),
        COMPLETED("Đã hoàn tất"),
        CANCELLED("Đã hủy");

        private final String label;

        OrderStatus(String label) {
            this.label = label;
        }

        private boolean isSellerTransitionFrom(OrderStatus oldStatus) {
            return (oldStatus == PENDING && this == CONFIRMED)
                    || (oldStatus == CONFIRMED && this == PREPARING)
                    || (oldStatus == PREPARING && this == SHIPPING)
                    || (oldStatus == SHIPPING && this == COMPLETED);
        }
    }

    private record NotificationCommand(Long receiverId, String title, String content) {
    }
}
