package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.ordering.api.OrderCreatedEvent;
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

class OrderCreatedNotificationListenerTest {

    private final NotificationWriter notificationWriter = mock(NotificationWriter.class);
    private final OrderCreatedNotificationListener listener =
            new OrderCreatedNotificationListener(notificationWriter);

    @Test
    void mapsEventToUnreadShopOwnerNotificationData() {
        OrderCreatedEvent event = validEvent();

        listener.onOrderCreated(event);

        verify(notificationWriter).create(
                event.eventId(),
                event.shopOwnerId(),
                NotificationType.ORDER_CREATED,
                OrderCreatedNotificationListener.TITLE,
                "Đơn hàng ORD-123 vừa được tạo thành công cho gian hàng của bạn.",
                event.orderId());
    }

    @Test
    void rejectsInvalidEvents() {
        UUID eventId = UUID.randomUUID();
        Instant createdAt = Instant.now();
        List<OrderCreatedEvent> invalidEvents = List.of(
                new OrderCreatedEvent(null, 11L, 22L, 33L, 44L, 44L, "ORD-123", "PENDING", createdAt),
                new OrderCreatedEvent(eventId, 0L, 22L, 33L, 44L, 44L, "ORD-123", "PENDING", createdAt),
                new OrderCreatedEvent(eventId, 11L, 0L, 33L, 44L, 44L, "ORD-123", "PENDING", createdAt),
                new OrderCreatedEvent(eventId, 11L, 22L, 0L, 44L, 44L, "ORD-123", "PENDING", createdAt),
                new OrderCreatedEvent(eventId, 11L, 22L, 33L, 44L, 44L, "  ", "PENDING", createdAt),
                new OrderCreatedEvent(eventId, 11L, 22L, 33L, 44L, 44L,
                        "X".repeat(31), "PENDING", createdAt),
                new OrderCreatedEvent(eventId, 11L, 22L, 33L, 44L, 44L, "ORD-123", "PENDING", null));

        listener.onOrderCreated(null);
        invalidEvents.forEach(listener::onOrderCreated);

        verify(notificationWriter, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void trimsOrderCodeBeforeCreatingContent() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), 11L, 22L, 33L, 44L, 44L,
                "  ORD-TRIMMED  ", "PENDING", Instant.now());

        listener.onOrderCreated(event);

        verify(notificationWriter).create(
                event.eventId(), event.shopOwnerId(), NotificationType.ORDER_CREATED,
                OrderCreatedNotificationListener.TITLE,
                "Đơn hàng ORD-TRIMMED vừa được tạo thành công cho gian hàng của bạn.",
                event.orderId());
    }

    @Test
    void treatsDuplicateReplayAsHandled() {
        OrderCreatedEvent event = validEvent();
        doThrow(new DataIntegrityViolationException(
                "constraint uk_notifications_event_receiver violated"))
                .when(notificationWriter).create(any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> listener.onOrderCreated(event));
    }

    @Test
    void containsUnexpectedWriterFailureAfterCommit() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationWriter).create(any(), any(), any(), any(), any(), any());

        assertDoesNotThrow(() -> listener.onOrderCreated(validEvent()));
    }

    private OrderCreatedEvent validEvent() {
        return new OrderCreatedEvent(
                UUID.randomUUID(), 11L, 22L, 33L, 44L, 44L,
                "ORD-123", "PENDING", Instant.now());
    }
}
