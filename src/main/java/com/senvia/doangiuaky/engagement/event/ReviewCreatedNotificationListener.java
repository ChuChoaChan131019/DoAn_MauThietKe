package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.api.ReviewCreatedEvent;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Optional;

@Component
public class ReviewCreatedNotificationListener {

    static final String TITLE = "Sản phẩm của bạn có đánh giá mới";
    private static final String UNIQUE_CONSTRAINT = "uk_notifications_event_receiver";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(ReviewCreatedNotificationListener.class);

    private final MerchantApi merchantApi;
    private final NotificationWriter notificationWriter;

    public ReviewCreatedNotificationListener(MerchantApi merchantApi,
                                             NotificationWriter notificationWriter) {
        this.merchantApi = merchantApi;
        this.notificationWriter = notificationWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = false)
    public void onReviewCreated(ReviewCreatedEvent event) {
        if (!isValid(event)) {
            LOGGER.warn("Ignoring invalid ReviewCreatedEvent");
            return;
        }

        Optional<CartProductView> productResult = merchantApi.findProductForCart(event.productId());
        if (productResult == null || productResult.isEmpty()) {
            LOGGER.warn("Ignoring review event because product cannot be resolved, eventId={}, productId={}",
                    event.eventId(), event.productId());
            return;
        }

        CartProductView product = productResult.get();
        if (!isValidProduct(product, event.productId())) {
            LOGGER.warn("Ignoring review event because product ownership is invalid, eventId={}, productId={}",
                    event.eventId(), event.productId());
            return;
        }

        try {
            notificationWriter.create(
                    event.eventId(),
                    product.shopOwnerId(),
                    NotificationType.REVIEW_CREATED,
                    TITLE,
                    "Sản phẩm " + product.productName().trim()
                            + " vừa nhận được đánh giá " + event.rating() + " sao.",
                    event.productId());
        } catch (DataIntegrityViolationException exception) {
            if (isReplay(exception)) {
                LOGGER.debug("Review notification already exists for eventId={}, receiverId={}",
                        event.eventId(), product.shopOwnerId());
            } else {
                LOGGER.error("Unable to create review notification for eventId={}, productId={}, receiverId={}",
                        event.eventId(), event.productId(), product.shopOwnerId(), exception);
            }
        } catch (RuntimeException exception) {
            LOGGER.error("Unable to create review notification for eventId={}, productId={}, receiverId={}",
                    event.eventId(), event.productId(), product.shopOwnerId(), exception);
        }
    }

    private static boolean isValid(ReviewCreatedEvent event) {
        return event != null
                && event.eventId() != null
                && isPositive(event.reviewId())
                && isPositive(event.productId())
                && event.rating() >= 1
                && event.rating() <= 5
                && event.occurredAt() != null;
    }

    private static boolean isValidProduct(CartProductView product, Long expectedProductId) {
        return product != null
                && product.productId() != null
                && product.productId().equals(expectedProductId)
                && isPositive(product.shopOwnerId())
                && StringUtils.hasText(product.productName());
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
