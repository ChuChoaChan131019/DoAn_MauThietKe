package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.ordering.api.OrderCreatedEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component
public class OrderCreatedNotificationListener {

    static final String TITLE = "Bạn có đơn hàng mới";
    private static final String UNIQUE_CONSTRAINT = "uk_notifications_event_receiver";
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderCreatedNotificationListener.class);

    private final NotificationWriter notificationWriter;

    public OrderCreatedNotificationListener(NotificationWriter notificationWriter) {
        this.notificationWriter = notificationWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onOrderCreated(OrderCreatedEvent event) {
        if (!isValid(event)) {
            LOGGER.warn("Ignoring invalid OrderCreatedEvent");
            return;
        }

        String orderCode = event.orderCode().trim();
        try {
            notificationWriter.create(
                    event.eventId(),
                    event.shopOwnerId(),
                    NotificationType.ORDER_CREATED,
                    TITLE,
                    "Đơn hàng " + orderCode + " vừa được tạo thành công cho gian hàng của bạn.",
                    event.orderId());
        } catch (DataIntegrityViolationException exception) {
            if (isReplay(exception)) {
                LOGGER.debug("Order notification already exists for eventId={}, orderId={}, receiverId={}",
                        event.eventId(), event.orderId(), event.shopOwnerId());
            } else {
                LOGGER.error("Unable to create order notification for eventId={}, orderId={}, receiverId={}",
                        event.eventId(), event.orderId(), event.shopOwnerId(), exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to create order notification for eventId={}, orderId={}, receiverId={}",
                    event.eventId(), event.orderId(), event.shopOwnerId(), exception);
        }
    }

    private static boolean isValid(OrderCreatedEvent event) {
        return event != null
                && event.eventId() != null
                && isPositive(event.orderId())
                && isPositive(event.shopId())
                && isPositive(event.shopOwnerId())
                && StringUtils.hasText(event.orderCode())
                && event.orderCode().trim().length() <= 30
                && event.occurredAt() != null;
    }

    private static boolean isPositive(Long value) {
        return value != null && value > 0;
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
}
