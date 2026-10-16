package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private static final Long RECEIVER_ID = 17L;
    private NotificationRepository repository;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        repository = mock(NotificationRepository.class);
        CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
        when(currentUserApi.requireCurrentUser()).thenReturn(
                new UserSummary(RECEIVER_ID, "Receiver", UserRole.USER, AccountStatus.ACTIVE));
        service = new NotificationService(repository, currentUserApi);
    }

    @Test
    void normalizesPageBelowOneAndReturnsOnlyRepositoryPage() {
        Notification notification = notification();
        when(repository.findByReceiverId(eq(RECEIVER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(notification), PageRequest.of(0, 10), 1));
        when(repository.countByReceiverIdAndReadFalse(RECEIVER_ID)).thenReturn(1L);

        var result = service.getNotifications(-5);

        assertEquals(1, result.currentPage());
        assertEquals(1, result.items().size());
        assertEquals(1L, result.unreadCount());
    }

    @Test
    void pageBeyondLastIsClampedToLastPopulatedPage() {
        when(repository.findByReceiverId(eq(RECEIVER_ID), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(8, 10), 15))
                .thenReturn(new PageImpl<>(List.of(notification()), PageRequest.of(1, 10), 15));

        var result = service.getNotifications(9);

        assertEquals(2, result.currentPage());
        assertEquals(2, result.totalPages());
    }

    @Test
    void markReadIsIdempotentAndRejectsMissingOrForeignIdIdentically() {
        Notification notification = notification();
        when(repository.findByIdAndReceiverId(9L, RECEIVER_ID)).thenReturn(Optional.of(notification));
        when(repository.findByIdAndReceiverId(10L, RECEIVER_ID)).thenReturn(Optional.empty());

        assertTrue(service.markRead(9L));
        assertFalse(service.markRead(9L));
        assertThrows(NotificationNotFoundException.class, () -> service.markRead(10L));
    }

    @Test
    void markAllReadScopesBulkUpdateToCurrentReceiver() {
        when(repository.markAllReadByReceiverId(RECEIVER_ID)).thenReturn(3);

        assertEquals(3, service.markAllRead());
        verify(repository).markAllReadByReceiverId(RECEIVER_ID);
    }

    private Notification notification() {
        return Notification.create(UUID.randomUUID(), RECEIVER_ID, NotificationType.SHOP_REQUESTED,
                "Yêu cầu mới", "Có yêu cầu mở gian hàng mới.", 3L);
    }
}
