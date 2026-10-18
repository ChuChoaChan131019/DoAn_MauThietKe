package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import com.senvia.doangiuaky.ordering.api.OrderCreatedEvent;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrderCreatedNotificationIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void committedEventCreatesUnreadNotificationForShopOwner() {
        Long ownerId = insertUser();
        OrderCreatedEvent event = event(ownerId);

        publishCommitted(event);

        Notification notification = onlyNotification(event.eventId());
        assertEquals(ownerId, notification.getReceiverId());
        assertEquals(NotificationType.ORDER_CREATED, notification.getType());
        assertEquals(event.orderId(), notification.getReferenceId());
        assertEquals(OrderCreatedNotificationListener.TITLE, notification.getTitle());
        assertTrue(notification.getContent().contains(event.orderCode()));
        assertFalse(notification.isRead());
    }

    @Test
    void rolledBackAndNonTransactionalEventsDoNotCreateNotifications() {
        Long ownerId = insertUser();
        OrderCreatedEvent rolledBack = event(ownerId);
        OrderCreatedEvent nonTransactional = event(ownerId);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(rolledBack);
            status.setRollbackOnly();
        });
        eventPublisher.publishEvent(nonTransactional);

        assertEquals(0, notificationRepository.countByEventId(rolledBack.eventId()));
        assertEquals(0, notificationRepository.countByEventId(nonTransactional.eventId()));
    }

    @Test
    void replayDoesNotDuplicateAndDistinctEventsRemainIndependent() {
        Long ownerId = insertUser();
        OrderCreatedEvent first = event(ownerId);
        OrderCreatedEvent second = event(ownerId);

        publishCommitted(first);
        publishCommitted(first);
        publishCommitted(second);

        assertEquals(1, notificationRepository.countByEventId(first.eventId()));
        assertEquals(1, notificationRepository.countByEventId(second.eventId()));
    }

    private Notification onlyNotification(UUID eventId) {
        List<Notification> notifications =
                notificationRepository.findAllByEventIdOrderByReceiverIdAsc(eventId);
        assertEquals(1, notifications.size());
        return notifications.getFirst();
    }

    private void publishCommitted(Object event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(event));
    }

    private OrderCreatedEvent event(Long ownerId) {
        return new OrderCreatedEvent(
                UUID.randomUUID(), 91001L, 92001L, ownerId, 93001L, 93001L,
                "ORD-INTEGRATION", "PENDING", Instant.now());
    }

    private Long insertUser() {
        String email = "order-notification-owner-" + UUID.randomUUID() + "@example.test";
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values ('Order Notification Owner', ?, 'not-a-real-password-hash', 'USER', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, email);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }
}
