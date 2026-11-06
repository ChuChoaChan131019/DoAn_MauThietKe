package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.api.ReviewCreatedEvent;
import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@SpringBootTest
class ReviewCreatedNotificationIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private DemoMerchantApi merchantApi;

    @Test
    void committedEventCreatesUnreadNotificationForResolvedShopOwner() {
        Long ownerId = insertUser("review-notification-owner-" + UUID.randomUUID() + "@example.test");
        ReviewCreatedEvent event = event(19L, (short) 5);
        when(merchantApi.findProductForCart(19L)).thenReturn(OptionalHolder.product(ownerId, 19L));

        publishCommitted(event);

        Notification notification = onlyNotification(event.eventId());
        assertEquals(ownerId, notification.getReceiverId());
        assertEquals(NotificationType.REVIEW_CREATED, notification.getType());
        assertEquals(event.productId(), notification.getReferenceId());
        assertEquals(ReviewCreatedNotificationListener.TITLE, notification.getTitle());
        assertEquals("Sản phẩm Test Product vừa nhận được đánh giá 5 sao.", notification.getContent());
        assertFalse(notification.isRead());
    }

    @Test
    void rolledBackAndNonTransactionalEventsDoNotCreateNotifications() {
        Long ownerId = insertUser("review-notification-rollback-" + UUID.randomUUID() + "@example.test");
        when(merchantApi.findProductForCart(19L)).thenReturn(OptionalHolder.product(ownerId, 19L));
        ReviewCreatedEvent rolledBack = event(19L, (short) 4);
        ReviewCreatedEvent nonTransactional = event(19L, (short) 3);

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
        Long ownerId = insertUser("review-notification-replay-" + UUID.randomUUID() + "@example.test");
        when(merchantApi.findProductForCart(19L)).thenReturn(OptionalHolder.product(ownerId, 19L));
        ReviewCreatedEvent first = event(19L, (short) 5);
        ReviewCreatedEvent second = event(19L, (short) 4);

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

    private ReviewCreatedEvent event(Long productId, short rating) {
        return new ReviewCreatedEvent(UUID.randomUUID(), 91001L, productId, rating, Instant.now());
    }

    private Long insertUser(String email) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values ('Review Notification Owner', ?, 'not-a-real-password-hash', 'USER', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, email);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private static final class OptionalHolder {

        private static java.util.Optional<CartProductView> product(Long ownerId, Long productId) {
            return java.util.Optional.of(new CartProductView(
                    productId, 3L, ownerId, "Test Shop", "Test Product", null,
                    new BigDecimal("100.00"), 5, true, true, true, true));
        }
    }
}
