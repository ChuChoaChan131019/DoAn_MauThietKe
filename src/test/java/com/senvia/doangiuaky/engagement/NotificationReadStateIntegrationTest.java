package com.senvia.doangiuaky.engagement;

import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import com.senvia.doangiuaky.engagement.service.NotificationNotFoundException;
import com.senvia.doangiuaky.engagement.service.NotificationService;
import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class NotificationReadStateIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private CurrentUserApi currentUserApi;

    @Test
    void receiverCannotReadAnotherUsersNotificationAndReadAllRemainsIsolated() {
        Long receiverA = insertUser("notification-a-" + UUID.randomUUID() + "@example.test");
        Long receiverB = insertUser("notification-b-" + UUID.randomUUID() + "@example.test");
        Notification firstA = notificationRepository.saveAndFlush(notification(receiverA));
        notificationRepository.saveAndFlush(notification(receiverA));
        Notification onlyB = notificationRepository.saveAndFlush(notification(receiverB));
        when(currentUserApi.requireCurrentUser()).thenReturn(
                new UserSummary(receiverA, "Receiver A", UserRole.USER, AccountStatus.ACTIVE));

        assertThrows(NotificationNotFoundException.class,
                () -> notificationService.markRead(onlyB.getId()));
        assertTrue(notificationService.markRead(firstA.getId()));
        assertFalse(notificationService.markRead(firstA.getId()));
        assertEquals(1, notificationService.markAllRead());
        assertEquals(0, notificationService.markAllRead());

        assertEquals(0, notificationRepository.countByReceiverIdAndReadFalse(receiverA));
        assertEquals(1, notificationRepository.countByReceiverIdAndReadFalse(receiverB));
        assertFalse(notificationRepository.findById(onlyB.getId()).orElseThrow().isRead());
    }

    private Notification notification(Long receiverId) {
        return Notification.create(UUID.randomUUID(), receiverId, NotificationType.SHOP_REQUESTED,
                "Yêu cầu mới", "Có yêu cầu mở gian hàng mới.", 3L);
    }

    private Long insertUser(String email) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Notification Receiver", email, "not-a-real-password-hash");
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }
}
