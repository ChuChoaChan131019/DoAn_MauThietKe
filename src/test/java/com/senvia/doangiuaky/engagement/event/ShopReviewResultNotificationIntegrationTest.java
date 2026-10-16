package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import com.senvia.doangiuaky.merchant.api.ShopApprovedEvent;
import com.senvia.doangiuaky.merchant.api.ShopRejectedEvent;
import com.senvia.doangiuaky.merchant.service.ShopApprovalService;
import com.senvia.doangiuaky.merchant.service.ShopRejectionService;
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
class ShopReviewResultNotificationIntegrationTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ShopApprovalService shopApprovalService;

    @Autowired
    private ShopRejectionService shopRejectionService;

    @Test
    void committedEventsCreateUnreadNotificationsForTheOwner() {
        Long ownerId = insertUser("notification-result-owner-" + UUID.randomUUID() + "@example.test", "USER");
        ShopApprovedEvent approved = approvedEvent(ownerId);
        ShopRejectedEvent rejected = rejectedEvent(ownerId);

        publishCommitted(approved);
        publishCommitted(rejected);

        Notification approvedNotification = onlyNotification(approved.eventId());
        assertEquals(ownerId, approvedNotification.getReceiverId());
        assertEquals(NotificationType.SHOP_APPROVED, approvedNotification.getType());
        assertEquals(approved.shopId(), approvedNotification.getReferenceId());
        assertEquals(ShopReviewResultNotificationListener.APPROVED_TITLE,
                approvedNotification.getTitle());
        assertTrue(approvedNotification.getContent().contains("Result Shop"));
        assertFalse(approvedNotification.isRead());

        Notification rejectedNotification = onlyNotification(rejected.eventId());
        assertEquals(ownerId, rejectedNotification.getReceiverId());
        assertEquals(NotificationType.SHOP_REJECTED, rejectedNotification.getType());
        assertEquals(rejected.shopId(), rejectedNotification.getReferenceId());
        assertEquals(ShopReviewResultNotificationListener.REJECTED_TITLE,
                rejectedNotification.getTitle());
        assertTrue(rejectedNotification.getContent().contains("Thiếu giấy tờ đăng ký"));
        assertFalse(rejectedNotification.isRead());
    }

    @Test
    void rolledBackAndNonTransactionalEventsDoNotCreateNotifications() {
        Long ownerId = insertUser("notification-result-rollback-" + UUID.randomUUID() + "@example.test", "USER");
        ShopApprovedEvent rolledBackApproved = approvedEvent(ownerId);
        ShopRejectedEvent rolledBackRejected = rejectedEvent(ownerId);
        ShopApprovedEvent nonTransactionalApproved = approvedEvent(ownerId);
        ShopRejectedEvent nonTransactionalRejected = rejectedEvent(ownerId);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(rolledBackApproved);
            eventPublisher.publishEvent(rolledBackRejected);
            status.setRollbackOnly();
        });
        eventPublisher.publishEvent(nonTransactionalApproved);
        eventPublisher.publishEvent(nonTransactionalRejected);

        assertEquals(0, notificationRepository.countByEventId(rolledBackApproved.eventId()));
        assertEquals(0, notificationRepository.countByEventId(rolledBackRejected.eventId()));
        assertEquals(0, notificationRepository.countByEventId(nonTransactionalApproved.eventId()));
        assertEquals(0, notificationRepository.countByEventId(nonTransactionalRejected.eventId()));
    }

    @Test
    void replayingEitherEventDoesNotCreateDuplicates() {
        Long ownerId = insertUser("notification-result-replay-" + UUID.randomUUID() + "@example.test", "USER");
        ShopApprovedEvent approved = approvedEvent(ownerId);
        ShopRejectedEvent rejected = rejectedEvent(ownerId);

        publishCommitted(approved);
        publishCommitted(approved);
        publishCommitted(rejected);
        publishCommitted(rejected);

        assertEquals(1, notificationRepository.countByEventId(approved.eventId()));
        assertEquals(1, notificationRepository.countByEventId(rejected.eventId()));
    }

    @Test
    void distinctEventsForTheSameOwnerCreateIndependentNotifications() {
        Long ownerId = insertUser("notification-result-distinct-" + UUID.randomUUID() + "@example.test", "USER");
        ShopApprovedEvent first = approvedEvent(ownerId);
        ShopApprovedEvent second = approvedEvent(ownerId);

        publishCommitted(first);
        publishCommitted(second);

        assertEquals(1, notificationRepository.countByEventId(first.eventId()));
        assertEquals(1, notificationRepository.countByEventId(second.eventId()));
    }

    @Test
    void merchantApprovalAndRejectionServicesConnectToEngagementAfterCommit() {
        Long adminId = insertUser("notification-result-admin-" + UUID.randomUUID() + "@example.test", "ADMIN");
        Long approvedOwnerId = insertUser(
                "notification-result-approved-owner-" + UUID.randomUUID() + "@example.test", "USER");
        Long rejectedOwnerId = insertUser(
                "notification-result-rejected-owner-" + UUID.randomUUID() + "@example.test", "USER");
        Long approvedShopId = insertShop(approvedOwnerId, "Approved Service Shop");
        Long rejectedShopId = insertShop(rejectedOwnerId, "Rejected Service Shop");

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> shopApprovalService.approve(approvedShopId, adminId));
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> shopRejectionService.reject(
                        rejectedShopId, adminId, "  Thiếu giấy phép kinh doanh  "));

        assertStoredResult(approvedShopId, approvedOwnerId, "SHOP_APPROVED", null);
        assertStoredResult(
                rejectedShopId, rejectedOwnerId, "SHOP_REJECTED", "Thiếu giấy phép kinh doanh");
    }

    private void assertStoredResult(Long shopId, Long ownerId, String type, String contentFragment) {
        List<Long> receivers = jdbcTemplate.queryForList(
                "select receiver_id from notifications where reference_id = ? and type = ?",
                Long.class, shopId, type);
        assertEquals(List.of(ownerId), receivers);
        if (contentFragment != null) {
            String content = jdbcTemplate.queryForObject(
                    "select content from notifications where reference_id = ? and type = ?",
                    String.class, shopId, type);
            assertTrue(content.contains(contentFragment));
        }
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

    private ShopApprovedEvent approvedEvent(Long ownerId) {
        return new ShopApprovedEvent(
                UUID.randomUUID(), 90210L, ownerId, "  Result Shop  ", 1L, Instant.now());
    }

    private ShopRejectedEvent rejectedEvent(Long ownerId) {
        return new ShopRejectedEvent(
                UUID.randomUUID(), 90211L, ownerId, "  Result Shop  ",
                "  Thiếu giấy tờ đăng ký  ", 1L, Instant.now());
    }

    private Long insertUser(String email, String role) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Notification Result User", email, "not-a-real-password-hash", role);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long insertShop(Long ownerId, String shopName) {
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status, submitted_at, created_at, updated_at)
                values (?, ?, ?, ?, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName, "0900000000", "Test address");
        return jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);
    }
}
