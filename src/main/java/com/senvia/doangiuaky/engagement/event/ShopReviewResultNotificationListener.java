package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.merchant.api.ShopApprovedEvent;
import com.senvia.doangiuaky.merchant.api.ShopRejectedEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.UUID;

@Component
public class ShopReviewResultNotificationListener {

    static final String APPROVED_TITLE = "Gian hàng đã được phê duyệt";
    static final String REJECTED_TITLE = "Yêu cầu mở gian hàng bị từ chối";
    private static final String UNIQUE_CONSTRAINT = "uk_notifications_event_receiver";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ShopReviewResultNotificationListener.class);

    private final NotificationWriter notificationWriter;

    public ShopReviewResultNotificationListener(NotificationWriter notificationWriter) {
        this.notificationWriter = notificationWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onShopApproved(ShopApprovedEvent event) {
        if (!isValid(event)) {
            LOGGER.warn("Ignoring invalid ShopApprovedEvent");
            return;
        }

        String shopName = event.shopName().trim();
        createNotification(
                event.eventId(),
                event.ownerId(),
                NotificationType.SHOP_APPROVED,
                APPROVED_TITLE,
                "Gian hàng \"" + shopName
                        + "\" đã được phê duyệt. Bạn có thể bắt đầu quản lý gian hàng.",
                event.shopId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onShopRejected(ShopRejectedEvent event) {
        if (!isValid(event)) {
            LOGGER.warn("Ignoring invalid ShopRejectedEvent");
            return;
        }

        String shopName = event.shopName().trim();
        String rejectionReason = event.rejectionReason().trim();
        createNotification(
                event.eventId(),
                event.ownerId(),
                NotificationType.SHOP_REJECTED,
                REJECTED_TITLE,
                "Gian hàng \"" + shopName + "\" bị từ chối. Lý do: " + rejectionReason,
                event.shopId());
    }

    private void createNotification(UUID eventId, Long receiverId, NotificationType type,
                                    String title, String content, Long referenceId) {
        try {
            notificationWriter.create(eventId, receiverId, type, title, content, referenceId);
        } catch (DataIntegrityViolationException exception) {
            if (isReplay(exception)) {
                LOGGER.debug("Shop review result notification already exists for eventId={}, receiverId={}",
                        eventId, receiverId);
            } else {
                LOGGER.error("Unable to create shop review result notification for eventId={}, receiverId={}",
                        eventId, receiverId, exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to create shop review result notification for eventId={}, receiverId={}",
                    eventId, receiverId, exception);
        }
    }

    private static boolean isValid(ShopApprovedEvent event) {
        return event != null
                && event.eventId() != null
                && isPositive(event.shopId())
                && isPositive(event.ownerId())
                && StringUtils.hasText(event.shopName())
                && event.approvedAt() != null;
    }

    private static boolean isValid(ShopRejectedEvent event) {
        return event != null
                && event.eventId() != null
                && isPositive(event.shopId())
                && isPositive(event.ownerId())
                && StringUtils.hasText(event.shopName())
                && StringUtils.hasText(event.rejectionReason())
                && event.rejectedAt() != null;
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
