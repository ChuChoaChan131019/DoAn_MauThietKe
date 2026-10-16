package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationWriter;
import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.ShopRequestedEvent;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopRequestedNotificationListenerTest {

    private final IdentityApi identityApi = mock(IdentityApi.class);
    private final NotificationWriter notificationWriter = mock(NotificationWriter.class);
    private final ShopRequestedNotificationListener listener =
            new ShopRequestedNotificationListener(identityApi, notificationWriter);

    @Test
    void createsOneNotificationForEachDistinctActiveAdmin() {
        ShopRequestedEvent event = validEvent();
        UserSummary activeAdmin = new UserSummary(10L, "Active Admin", UserRole.ADMIN, AccountStatus.ACTIVE);
        when(identityApi.findActiveAdmins()).thenReturn(List.of(
                activeAdmin,
                activeAdmin,
                new UserSummary(11L, "Locked Admin", UserRole.ADMIN, AccountStatus.LOCKED),
                new UserSummary(12L, "Regular User", UserRole.USER, AccountStatus.ACTIVE)));

        listener.onShopRequested(event);

        verify(notificationWriter, times(1)).create(
                event.eventId(),
                10L,
                NotificationType.SHOP_REQUESTED,
                ShopRequestedNotificationListener.TITLE,
                "Gian hàng \"Example Shop\" vừa gửi yêu cầu mở gian hàng.",
                event.shopId());
        verify(notificationWriter, times(1)).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void doesNothingWhenThereAreNoActiveAdmins() {
        when(identityApi.findActiveAdmins()).thenReturn(List.of());

        listener.onShopRequested(validEvent());

        verify(notificationWriter, never()).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsInvalidEventBeforeCallingIdentityApi() {
        ShopRequestedEvent invalid = new ShopRequestedEvent(
                null, 22L, 7L, "Example Shop", Instant.now());

        listener.onShopRequested(invalid);

        verify(identityApi, never()).findActiveAdmins();
        verify(notificationWriter, never()).create(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void failureForOneReceiverDoesNotStopTheRemainingReceivers() {
        ShopRequestedEvent event = validEvent();
        when(identityApi.findActiveAdmins()).thenReturn(List.of(
                new UserSummary(20L, "First Admin", UserRole.ADMIN, AccountStatus.ACTIVE),
                new UserSummary(21L, "Second Admin", UserRole.ADMIN, AccountStatus.ACTIVE)));
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationWriter).create(
                        event.eventId(),
                        20L,
                        NotificationType.SHOP_REQUESTED,
                        ShopRequestedNotificationListener.TITLE,
                        "Gian hàng \"Example Shop\" vừa gửi yêu cầu mở gian hàng.",
                        event.shopId());

        listener.onShopRequested(event);

        verify(notificationWriter).create(
                event.eventId(),
                21L,
                NotificationType.SHOP_REQUESTED,
                ShopRequestedNotificationListener.TITLE,
                "Gian hàng \"Example Shop\" vừa gửi yêu cầu mở gian hàng.",
                event.shopId());
    }

    private ShopRequestedEvent validEvent() {
        return new ShopRequestedEvent(
                UUID.randomUUID(), 22L, 7L, "  Example Shop  ", Instant.now());
    }
}
