package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.ordering.api.OrderStatusChangedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
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

class OrderStatusChangedNotificationListenerTest {

    private static final Long ORDER_ID = 101L;
    private static final Long SHOP_ID = 202L;
    private static final Long BUYER_ID = 303L;
    private static final Long OWNER_ID = 404L;
    private static final Long ADMIN_ID = 505L;
    private static final Long OUTSIDER_ID = 606L;

    private final MerchantApi merchantApi = mock(MerchantApi.class);
    private final IdentityApi identityApi = mock(IdentityApi.class);
    private final NotificationWriter notificationWriter = mock(NotificationWriter.class);
    private final OrderStatusChangedNotificationListener listener =
            new OrderStatusChangedNotificationListener(merchantApi, identityApi, notificationWriter);

    @Test
    void sellerTransitionNotifiesBuyerOnlyWithMappedStatusLabels() {
        givenShopAndUser(OWNER_ID, UserRole.USER);
        OrderStatusChangedEvent event = event(OWNER_ID, "PENDING", "CONFIRMED");

        listener.onOrderStatusChanged(event);

        verify(notificationWriter).create(
                event.eventId(),
                BUYER_ID,
                NotificationType.ORDER_STATUS_CHANGED,
                OrderStatusChangedNotificationListener.STATUS_CHANGED_TITLE,
                "Đơn hàng ORD-101 đã chuyển từ Chờ xử lý sang Đã xác nhận.",
                ORDER_ID);
        verify(notificationWriter, never()).create(
                any(), eq(OWNER_ID), any(), any(), any(), any());
    }

    @Test
    void sellerCancellationNotifiesBuyerOnly() {
        givenShopAndUser(OWNER_ID, UserRole.USER);
        OrderStatusChangedEvent event = event(OWNER_ID, "PREPARING", "CANCELLED");

        listener.onOrderStatusChanged(event);

        verify(notificationWriter).create(
                event.eventId(),
                BUYER_ID,
                NotificationType.ORDER_STATUS_CHANGED,
                OrderStatusChangedNotificationListener.SELLER_CANCELLED_TITLE,
                "Đơn hàng ORD-101 đã bị gian hàng hủy.",
                ORDER_ID);
        verify(notificationWriter, never()).create(
                any(), eq(OWNER_ID), any(), any(), any(), any());
    }

    @Test
    void buyerCancellationNotifiesShopOwnerOnly() {
        givenShopAndUser(BUYER_ID, UserRole.USER);
        OrderStatusChangedEvent event = event(BUYER_ID, "CONFIRMED", "CANCELLED");

        listener.onOrderStatusChanged(event);

        verify(notificationWriter).create(
                event.eventId(),
                OWNER_ID,
                NotificationType.ORDER_STATUS_CHANGED,
                OrderStatusChangedNotificationListener.BUYER_CANCELLED_TITLE,
                "Người mua đã hủy đơn hàng ORD-101.",
                ORDER_ID);
        verify(notificationWriter, never()).create(
                any(), eq(BUYER_ID), any(), any(), any(), any());
    }

    @Test
    void adminCancellationNotifiesBuyerAndShopOwnerWithTheSameEventId() {
        givenShopAndUser(ADMIN_ID, UserRole.ADMIN);
        OrderStatusChangedEvent event = event(ADMIN_ID, "PENDING", "CANCELLED");

        listener.onOrderStatusChanged(event);

        verify(notificationWriter).create(
                event.eventId(),
                BUYER_ID,
                NotificationType.ORDER_STATUS_CHANGED,
                OrderStatusChangedNotificationListener.ADMIN_CANCELLED_BUYER_TITLE,
                "Đơn hàng ORD-101 đã bị quản trị viên hủy.",
                ORDER_ID);
        verify(notificationWriter).create(
                event.eventId(),
                OWNER_ID,
                NotificationType.ORDER_STATUS_CHANGED,
                OrderStatusChangedNotificationListener.ADMIN_CANCELLED_OWNER_TITLE,
                "Quản trị viên đã hủy đơn hàng ORD-101.",
                ORDER_ID);
    }

    @Test
    void invalidEventOrTransitionDoesNotResolveReceiversOrWrite() {
        listener.onOrderStatusChanged(null);
        listener.onOrderStatusChanged(new OrderStatusChangedEvent(
                null, ORDER_ID, SHOP_ID, BUYER_ID, OWNER_ID,
                "ORD-101", "PENDING", "CONFIRMED", Instant.now()));
        listener.onOrderStatusChanged(new OrderStatusChangedEvent(
                UUID.randomUUID(), ORDER_ID, SHOP_ID, BUYER_ID, OWNER_ID,
                "ORD-101", "COMPLETED", "PREPARING", Instant.now()));
        listener.onOrderStatusChanged(new OrderStatusChangedEvent(
                UUID.randomUUID(), ORDER_ID, SHOP_ID, BUYER_ID, OWNER_ID,
                "ORD-101", "PENDING", "CONFIRMED", null));

        verify(notificationWriter, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresBuyerNonCancellationAndOutsiderUserEvents() {
        givenShopAndUser(BUYER_ID, UserRole.USER);
        listener.onOrderStatusChanged(event(BUYER_ID, "PENDING", "CONFIRMED"));

        givenShopAndUser(OUTSIDER_ID, UserRole.USER);
        listener.onOrderStatusChanged(event(OUTSIDER_ID, "PENDING", "CANCELLED"));

        verify(notificationWriter, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void ignoresEventWhenOwnerOrActorCannotBeResolved() {
        when(merchantApi.findShopSummary(SHOP_ID)).thenReturn(Optional.empty());
        listener.onOrderStatusChanged(event(OWNER_ID, "PENDING", "CONFIRMED"));

        when(merchantApi.findShopSummary(SHOP_ID)).thenReturn(Optional.of(
                new MerchantShopSummary(SHOP_ID, OWNER_ID, "Example Shop", true, true)));
        when(identityApi.findUser(OWNER_ID)).thenReturn(Optional.empty());
        listener.onOrderStatusChanged(event(OWNER_ID, "PENDING", "CONFIRMED"));

        verify(notificationWriter, never()).create(any(), any(), any(), any(), any(), any());
    }

    @Test
    void treatsDuplicateAsReplayAndStillAttemptsTheSecondAdminReceiver() {
        givenShopAndUser(ADMIN_ID, UserRole.ADMIN);
        OrderStatusChangedEvent event = event(ADMIN_ID, "PENDING", "CANCELLED");
        doThrow(new DataIntegrityViolationException(
                "constraint uk_notifications_event_receiver violated"))
                .when(notificationWriter).create(
                        event.eventId(),
                        BUYER_ID,
                        NotificationType.ORDER_STATUS_CHANGED,
                        OrderStatusChangedNotificationListener.ADMIN_CANCELLED_BUYER_TITLE,
                        "Đơn hàng ORD-101 đã bị quản trị viên hủy.",
                        ORDER_ID);

        assertDoesNotThrow(() -> listener.onOrderStatusChanged(event));

        verify(notificationWriter).create(
                event.eventId(),
                OWNER_ID,
                NotificationType.ORDER_STATUS_CHANGED,
                OrderStatusChangedNotificationListener.ADMIN_CANCELLED_OWNER_TITLE,
                "Quản trị viên đã hủy đơn hàng ORD-101.",
                ORDER_ID);
    }

    @Test
    void containsUnexpectedWriterFailureAfterCommit() {
        givenShopAndUser(ADMIN_ID, UserRole.ADMIN);
        OrderStatusChangedEvent event = event(ADMIN_ID, "PENDING", "CANCELLED");
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationWriter).create(
                        any(), eq(BUYER_ID), any(), any(), any(), any());

        assertDoesNotThrow(() -> listener.onOrderStatusChanged(event));

        verify(notificationWriter).create(
                event.eventId(),
                OWNER_ID,
                NotificationType.ORDER_STATUS_CHANGED,
                OrderStatusChangedNotificationListener.ADMIN_CANCELLED_OWNER_TITLE,
                "Quản trị viên đã hủy đơn hàng ORD-101.",
                ORDER_ID);
    }

    private void givenShopAndUser(Long actorId, UserRole role) {
        when(merchantApi.findShopSummary(SHOP_ID)).thenReturn(Optional.of(
                new MerchantShopSummary(SHOP_ID, OWNER_ID, "Example Shop", true, true)));
        when(identityApi.findUser(actorId)).thenReturn(Optional.of(
                new UserSummary(actorId, role == UserRole.ADMIN ? "Admin" : "User", role, AccountStatus.ACTIVE)));
    }

    private OrderStatusChangedEvent event(Long actorId, String oldStatus, String newStatus) {
        return new OrderStatusChangedEvent(
                UUID.randomUUID(), ORDER_ID, SHOP_ID, BUYER_ID, actorId,
                " ORD-101 ", oldStatus, newStatus, Instant.now());
    }
}
