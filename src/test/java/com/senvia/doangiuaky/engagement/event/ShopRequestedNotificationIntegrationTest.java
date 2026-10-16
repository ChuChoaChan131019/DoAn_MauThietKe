package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.merchant.api.ShopRequestedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class ShopRequestedNotificationIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private IdentityApi identityApi;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void committedEventCreatesOneUnreadNotificationForEveryActiveAdmin() {
        Long firstAdminId = insertUser("notification-commit-first-" + UUID.randomUUID() + "@example.test", "ADMIN");
        Long secondAdminId = insertUser("notification-commit-second-" + UUID.randomUUID() + "@example.test", "ADMIN");
        Long userId = insertUser("notification-commit-user-" + UUID.randomUUID() + "@example.test", "USER");
        ShopRequestedEvent event = event();
        Set<Long> expectedReceiverIds = identityApi.findActiveAdmins().stream()
                .map(summary -> summary.userId())
                .collect(Collectors.toSet());

        publishCommitted(event);

        List<Notification> notifications = notificationRepository.findAllByEventIdOrderByReceiverIdAsc(event.eventId());
        assertEquals(expectedReceiverIds, notifications.stream()
                .map(Notification::getReceiverId)
                .collect(Collectors.toSet()));
        assertEquals(expectedReceiverIds.size(), notifications.size());
        assertFalse(notifications.isEmpty());
        notifications.forEach(notification -> {
            assertEquals(NotificationType.SHOP_REQUESTED, notification.getType());
            assertEquals(event.shopId(), notification.getReferenceId());
            assertFalse(notification.isRead());
        });
        assertEquals(1, notifications.stream().filter(item -> item.getReceiverId().equals(firstAdminId)).count());
        assertEquals(1, notifications.stream().filter(item -> item.getReceiverId().equals(secondAdminId)).count());
        assertEquals(0, notifications.stream().filter(item -> item.getReceiverId().equals(userId)).count());
    }

    @Test
    void rolledBackOrNonTransactionalEventDoesNotCreateNotifications() {
        insertUser("notification-rollback-admin-" + UUID.randomUUID() + "@example.test", "ADMIN");
        ShopRequestedEvent rolledBackEvent = event();
        ShopRequestedEvent nonTransactionalEvent = event();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(rolledBackEvent);
            status.setRollbackOnly();
        });
        eventPublisher.publishEvent(nonTransactionalEvent);

        assertEquals(0, notificationRepository.countByEventId(rolledBackEvent.eventId()));
        assertEquals(0, notificationRepository.countByEventId(nonTransactionalEvent.eventId()));
    }

    @Test
    void replayingTheSameEventDoesNotCreateDuplicates() {
        insertUser("notification-replay-admin-" + UUID.randomUUID() + "@example.test", "ADMIN");
        ShopRequestedEvent event = event();
        long expectedReceiverCount = identityApi.findActiveAdmins().stream()
                .map(summary -> summary.userId())
                .distinct()
                .count();

        publishCommitted(event);
        publishCommitted(event);

        assertEquals(expectedReceiverCount, notificationRepository.countByEventId(event.eventId()));
    }

    private void publishCommitted(ShopRequestedEvent event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(event));
    }

    private ShopRequestedEvent event() {
        return new ShopRequestedEvent(
                UUID.randomUUID(), 90210L, 12L, "Integration Shop", Instant.now());
    }

    private Long insertUser(String email, String role) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Notification Integration User", email, "not-a-real-password-hash", role);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }
}
