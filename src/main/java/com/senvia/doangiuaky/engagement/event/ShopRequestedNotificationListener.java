package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.ShopRequestedEvent;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ShopRequestedNotificationListener {

    static final String TITLE = "Yêu cầu mở gian hàng mới";
    private static final String UNIQUE_CONSTRAINT = "uk_notifications_event_receiver";
    private static final Logger LOGGER = LoggerFactory.getLogger(ShopRequestedNotificationListener.class);

    private final IdentityApi identityApi;
    private final NotificationWriter notificationWriter;

    public ShopRequestedNotificationListener(IdentityApi identityApi,
                                             NotificationWriter notificationWriter) {
        this.identityApi = identityApi;
        this.notificationWriter = notificationWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onShopRequested(ShopRequestedEvent event) {
        if (!isValid(event)) {
            LOGGER.warn("Ignoring invalid ShopRequestedEvent");
            return;
        }

        List<UserSummary> activeAdmins;
        try {
            activeAdmins = identityApi.findActiveAdmins();
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to resolve notification receivers for shop request eventId={}",
                    event.eventId(), exception);
            return;
        }

        Map<Long, UserSummary> distinctAdmins = new LinkedHashMap<>();
        if (activeAdmins != null) {
            activeAdmins.stream()
                    .filter(this::isActiveAdmin)
                    .forEach(admin -> distinctAdmins.putIfAbsent(admin.userId(), admin));
        }

        String content = "Gian hàng \"" + event.shopName().trim()
                + "\" vừa gửi yêu cầu mở gian hàng.";
        distinctAdmins.keySet().forEach(receiverId -> createForReceiver(event, receiverId, content));
    }

    private void createForReceiver(ShopRequestedEvent event, Long receiverId, String content) {
        try {
            notificationWriter.create(
                    event.eventId(),
                    receiverId,
                    NotificationType.SHOP_REQUESTED,
                    TITLE,
                    content,
                    event.shopId());
        } catch (DataIntegrityViolationException exception) {
            if (isReplay(exception)) {
                LOGGER.debug("Shop request notification already exists for eventId={}, receiverId={}",
                        event.eventId(), receiverId);
            } else {
                LOGGER.error("Unable to create shop request notification for eventId={}, receiverId={}",
                        event.eventId(), receiverId, exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to create shop request notification for eventId={}, receiverId={}",
                    event.eventId(), receiverId, exception);
        }
    }

    private boolean isActiveAdmin(UserSummary candidate) {
        return candidate != null
                && candidate.userId() != null
                && candidate.userId() > 0
                && candidate.role() == UserRole.ADMIN
                && candidate.accountStatus() == AccountStatus.ACTIVE;
    }

    private static boolean isValid(ShopRequestedEvent event) {
        return event != null
                && event.eventId() != null
                && event.shopId() != null
                && event.shopId() > 0
                && event.ownerId() != null
                && event.ownerId() > 0
                && StringUtils.hasText(event.shopName())
                && event.submittedAt() != null;
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
