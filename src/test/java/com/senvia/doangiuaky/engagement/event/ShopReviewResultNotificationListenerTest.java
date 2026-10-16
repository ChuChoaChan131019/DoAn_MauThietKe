package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.merchant.api.ShopApprovedEvent;
import com.senvia.doangiuaky.merchant.api.ShopRejectedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ShopReviewResultNotificationListenerTest {

    private final NotificationWriter notificationWriter = mock(NotificationWriter.class);
    private final ShopReviewResultNotificationListener listener =
            new ShopReviewResultNotificationListener(notificationWriter);

    @Test
    void mapsApprovedEventToOwnerNotification() {
        ShopApprovedEvent event = approvedEvent();

        listener.onShopApproved(event);

        verify(notificationWriter).create(
                event.eventId(),
                event.ownerId(),
                NotificationType.SHOP_APPROVED,
                ShopReviewResultNotificationListener.APPROVED_TITLE,
                "Gian hàng \"Example Shop\" đã được phê duyệt. Bạn có thể bắt đầu quản lý gian hàng.",
                event.shopId());
    }

    @Test
    void mapsRejectedEventAndTrimsReason() {
        ShopRejectedEvent event = rejectedEvent();

        listener.onShopRejected(event);

        verify(notificationWriter).create(
                event.eventId(),
                event.ownerId(),
                NotificationType.SHOP_REJECTED,
                ShopReviewResultNotificationListener.REJECTED_TITLE,
                "Gian hàng \"Example Shop\" bị từ chối. Lý do: Thiếu giấy tờ đăng ký",
                event.shopId());
    }

    @Test
    void rejectsInvalidApprovedEvents() {
        Instant now = Instant.now();
        List<ShopApprovedEvent> invalidEvents = List.of(
                new ShopApprovedEvent(null, 22L, 7L, "Shop", 1L, now),
                new ShopApprovedEvent(UUID.randomUUID(), 0L, 7L, "Shop", 1L, now),
                new ShopApprovedEvent(UUID.randomUUID(), 22L, 0L, "Shop", 1L, now),
                new ShopApprovedEvent(UUID.randomUUID(), 22L, 7L, "  ", 1L, now),
                new ShopApprovedEvent(UUID.randomUUID(), 22L, 7L, "Shop", 1L, null));

        listener.onShopApproved(null);
        invalidEvents.forEach(listener::onShopApproved);

        verifyNoWrite();
    }

    @Test
    void rejectsInvalidRejectedEvents() {
        Instant now = Instant.now();
        List<ShopRejectedEvent> invalidEvents = List.of(
                new ShopRejectedEvent(null, 22L, 7L, "Shop", "Reason", 1L, now),
                new ShopRejectedEvent(UUID.randomUUID(), 0L, 7L, "Shop", "Reason", 1L, now),
                new ShopRejectedEvent(UUID.randomUUID(), 22L, 0L, "Shop", "Reason", 1L, now),
                new ShopRejectedEvent(UUID.randomUUID(), 22L, 7L, "  ", "Reason", 1L, now),
                new ShopRejectedEvent(UUID.randomUUID(), 22L, 7L, "Shop", "  ", 1L, now),
                new ShopRejectedEvent(UUID.randomUUID(), 22L, 7L, "Shop", "Reason", 1L, null));

        listener.onShopRejected(null);
        invalidEvents.forEach(listener::onShopRejected);

        verifyNoWrite();
    }

    @Test
    void treatsDuplicateReplayAsHandled() {
        ShopApprovedEvent event = approvedEvent();
        doThrow(new DataIntegrityViolationException(
                "constraint uk_notifications_event_receiver violated"))
                .when(notificationWriter).create(
                        event.eventId(),
                        event.ownerId(),
                        NotificationType.SHOP_APPROVED,
                        ShopReviewResultNotificationListener.APPROVED_TITLE,
                        "Gian hàng \"Example Shop\" đã được phê duyệt. Bạn có thể bắt đầu quản lý gian hàng.",
                        event.shopId());

        assertDoesNotThrow(() -> listener.onShopApproved(event));
    }

    @Test
    void containsUnexpectedWriterFailureAfterCommit() {
        ShopRejectedEvent event = rejectedEvent();
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationWriter).create(any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> listener.onShopRejected(event));
    }

    private void verifyNoWrite() {
        verify(notificationWriter, never()).create(any(), any(), any(), any(), any(), any());
    }

    private ShopApprovedEvent approvedEvent() {
        return new ShopApprovedEvent(
                UUID.randomUUID(), 22L, 7L, "  Example Shop  ", 1L, Instant.now());
    }

    private ShopRejectedEvent rejectedEvent() {
        return new ShopRejectedEvent(
                UUID.randomUUID(), 22L, 7L, "  Example Shop  ",
                "  Thiếu giấy tờ đăng ký  ", 1L, Instant.now());
    }
}
