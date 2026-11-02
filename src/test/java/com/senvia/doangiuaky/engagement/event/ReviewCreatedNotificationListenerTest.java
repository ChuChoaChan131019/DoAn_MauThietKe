package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.api.ReviewCreatedEvent;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewCreatedNotificationListenerTest {

    private final MerchantApi merchantApi = mock(MerchantApi.class);
    private final NotificationWriter notificationWriter = mock(NotificationWriter.class);
    private final ReviewCreatedNotificationListener listener =
            new ReviewCreatedNotificationListener(merchantApi, notificationWriter);

    @Test
    void createsNotificationForResolvedShopOwnerAndProduct() {
        ReviewCreatedEvent event = event(19L, (short) 5);
        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.of(product()));

        listener.onReviewCreated(event);

        verify(notificationWriter).create(
                event.eventId(),
                7L,
                NotificationType.REVIEW_CREATED,
                ReviewCreatedNotificationListener.TITLE,
                "Sản phẩm Senvia Product vừa nhận được đánh giá 5 sao.",
                19L);
    }

    @Test
    void ignoresInvalidEventsAndInvalidMerchantSummaries() {
        Instant now = Instant.now();
        List<ReviewCreatedEvent> invalidEvents = List.of(
                new ReviewCreatedEvent(null, 101L, 19L, (short) 5, now),
                new ReviewCreatedEvent(UUID.randomUUID(), 0L, 19L, (short) 5, now),
                new ReviewCreatedEvent(UUID.randomUUID(), 101L, 0L, (short) 5, now),
                new ReviewCreatedEvent(UUID.randomUUID(), 101L, 19L, (short) 0, now),
                new ReviewCreatedEvent(UUID.randomUUID(), 101L, 19L, (short) 6, now),
                new ReviewCreatedEvent(UUID.randomUUID(), 101L, 19L, (short) 5, null));

        listener.onReviewCreated(null);
        invalidEvents.forEach(listener::onReviewCreated);

        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.of(new CartProductView(
                20L, 3L, 7L, "Senvia Shop", "Senvia Product", null,
                new BigDecimal("100.00"), 5, true, true, true, true)));
        listener.onReviewCreated(event(19L, (short) 5));

        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.of(new CartProductView(
                19L, 3L, 0L, "Senvia Shop", "Senvia Product", null,
                new BigDecimal("100.00"), 5, true, true, true, true)));
        listener.onReviewCreated(event(19L, (short) 5));

        verify(notificationWriter, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresMissingProduct() {
        ReviewCreatedEvent event = event(19L, (short) 4);
        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.empty());

        listener.onReviewCreated(event);

        verify(notificationWriter, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void treatsDuplicateReplayAsHandled() {
        ReviewCreatedEvent event = event(19L, (short) 4);
        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.of(product()));
        doThrow(new DataIntegrityViolationException(
                "constraint uk_notifications_event_receiver violated"))
                .when(notificationWriter).create(
                        eq(event.eventId()), eq(7L), eq(NotificationType.REVIEW_CREATED),
                        eq(ReviewCreatedNotificationListener.TITLE),
                        eq("Sản phẩm Senvia Product vừa nhận được đánh giá 4 sao."), eq(19L));

        assertDoesNotThrow(() -> listener.onReviewCreated(event));
    }

    @Test
    void containsUnexpectedWriterFailureAfterCommit() {
        ReviewCreatedEvent event = event(19L, (short) 4);
        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.of(product()));
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationWriter).create(any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> listener.onReviewCreated(event));
    }

    private ReviewCreatedEvent event(Long productId, short rating) {
        return new ReviewCreatedEvent(UUID.randomUUID(), 101L, productId, rating, Instant.now());
    }

    private CartProductView product() {
        return new CartProductView(
                19L, 3L, 7L, "Senvia Shop", " Senvia Product ", null,
                new BigDecimal("100.00"), 5, true, true, true, true);
    }
}
