package com.senvia.doangiuaky.engagement.event;

import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import com.senvia.doangiuaky.ordering.api.OrderStatusChangedEvent;
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
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class OrderStatusChangedNotificationIntegrationTest {

    private static final AtomicLong ORDER_ID_SEQUENCE = new AtomicLong(700_000L);

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void committedSellerTransitionsNotifyBuyerOnly() {
        Fixture fixture = fixture("seller-transition");
        List<StatusTransition> transitions = List.of(
                new StatusTransition("PENDING", "CONFIRMED"),
                new StatusTransition("CONFIRMED", "PREPARING"),
                new StatusTransition("PREPARING", "SHIPPING"),
                new StatusTransition("SHIPPING", "COMPLETED"));

        for (StatusTransition transition : transitions) {
            OrderStatusChangedEvent event = event(
                    fixture, fixture.ownerId(), transition.oldStatus(), transition.newStatus());
            publishCommitted(event);

            Notification notification = onlyNotification(event.eventId());
            assertNotification(notification, fixture.buyerId(), event, "Đơn hàng " + event.orderCode());
            assertEquals(0, countForReceiver(event.eventId(), fixture.ownerId()));
        }
    }

    @Test
    void committedSellerCancellationNotifiesBuyerOnly() {
        Fixture fixture = fixture("seller-cancel");
        OrderStatusChangedEvent event = event(fixture, fixture.ownerId(), "CONFIRMED", "CANCELLED");

        publishCommitted(event);

        Notification notification = onlyNotification(event.eventId());
        assertEquals(fixture.buyerId(), notification.getReceiverId());
        assertEquals(OrderStatusChangedNotificationListener.SELLER_CANCELLED_TITLE, notification.getTitle());
        assertTrue(notification.getContent().contains("gian hàng hủy"));
        assertEquals(0, countForReceiver(event.eventId(), fixture.ownerId()));
    }

    @Test
    void committedBuyerCancellationNotifiesShopOwnerOnly() {
        Fixture fixture = fixture("buyer-cancel");
        OrderStatusChangedEvent event = event(fixture, fixture.buyerId(), "PENDING", "CANCELLED");

        publishCommitted(event);

        Notification notification = onlyNotification(event.eventId());
        assertEquals(fixture.ownerId(), notification.getReceiverId());
        assertEquals(OrderStatusChangedNotificationListener.BUYER_CANCELLED_TITLE, notification.getTitle());
        assertTrue(notification.getContent().contains("Người mua đã hủy"));
        assertEquals(0, countForReceiver(event.eventId(), fixture.buyerId()));
    }

    @Test
    void committedAdminCancellationNotifiesBothReceiversWithOneEventId() {
        Fixture fixture = fixture("admin-cancel");
        Long adminId = insertUser("notification-admin-" + UUID.randomUUID() + "@example.test", "ADMIN");
        OrderStatusChangedEvent event = event(fixture, adminId, "PREPARING", "CANCELLED");

        publishCommitted(event);

        List<Notification> notifications = notifications(event.eventId());
        assertEquals(2, notifications.size());
        assertEquals(List.of(fixture.buyerId(), fixture.ownerId()),
                notifications.stream().map(Notification::getReceiverId).sorted().toList());
        notifications.forEach(notification -> {
            assertEquals(event.eventId(), notification.getEventId());
            assertEquals(event.orderId(), notification.getReferenceId());
            assertEquals(NotificationType.ORDER_STATUS_CHANGED, notification.getType());
            assertFalse(notification.isRead());
            assertTrue(notification.getContent().contains(event.orderCode()));
        });
    }

    @Test
    void rollbackAndNonTransactionalEventsDoNotCreateNotifications() {
        Fixture fixture = fixture("transaction-boundary");
        OrderStatusChangedEvent rolledBack = event(fixture, fixture.ownerId(), "PENDING", "CONFIRMED");
        OrderStatusChangedEvent outsideTransaction = event(
                fixture, fixture.ownerId(), "CONFIRMED", "PREPARING");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(rolledBack);
            status.setRollbackOnly();
        });
        eventPublisher.publishEvent(outsideTransaction);

        assertEquals(0, notificationRepository.countByEventId(rolledBack.eventId()));
        assertEquals(0, notificationRepository.countByEventId(outsideTransaction.eventId()));
    }

    @Test
    void replayIsIdempotentPerReceiverButDifferentEventsRemainIndependent() {
        Fixture fixture = fixture("replay");
        OrderStatusChangedEvent first = event(fixture, fixture.ownerId(), "PENDING", "CONFIRMED");
        OrderStatusChangedEvent second = event(fixture, fixture.ownerId(), "CONFIRMED", "PREPARING");
        OrderStatusChangedEvent adminEvent = event(
                fixture,
                insertUser("notification-replay-admin-" + UUID.randomUUID() + "@example.test", "ADMIN"),
                "PREPARING",
                "CANCELLED");

        publishCommitted(first);
        publishCommitted(first);
        publishCommitted(second);
        publishCommitted(second);
        publishCommitted(adminEvent);
        publishCommitted(adminEvent);

        assertEquals(1, notificationRepository.countByEventId(first.eventId()));
        assertEquals(1, notificationRepository.countByEventId(second.eventId()));
        assertEquals(2, notificationRepository.countByEventId(adminEvent.eventId()));
        assertNotEquals(first.eventId(), second.eventId());
    }

    @Test
    void invalidTransitionAndUnknownUserEventAreIgnored() {
        Fixture fixture = fixture("invalid-event");
        OrderStatusChangedEvent invalidTransition = event(
                fixture, fixture.ownerId(), "COMPLETED", "PREPARING");
        OrderStatusChangedEvent unknownActor = event(
                fixture, 999999L, "PENDING", "CANCELLED");

        publishCommitted(invalidTransition);
        publishCommitted(unknownActor);

        assertEquals(0, notificationRepository.countByEventId(invalidTransition.eventId()));
        assertEquals(0, notificationRepository.countByEventId(unknownActor.eventId()));
    }

    private void assertNotification(Notification notification,
                                     Long receiverId,
                                     OrderStatusChangedEvent event,
                                     String contentFragment) {
        assertEquals(receiverId, notification.getReceiverId());
        assertEquals(NotificationType.ORDER_STATUS_CHANGED, notification.getType());
        assertEquals(event.orderId(), notification.getReferenceId());
        assertTrue(notification.getTitle() != null && !notification.getTitle().isBlank());
        assertTrue(notification.getContent().contains(contentFragment));
        assertFalse(notification.isRead());
    }

    private Notification onlyNotification(UUID eventId) {
        List<Notification> notifications = notifications(eventId);
        assertEquals(1, notifications.size());
        return notifications.getFirst();
    }

    private List<Notification> notifications(UUID eventId) {
        return notificationRepository.findAllByEventIdOrderByReceiverIdAsc(eventId);
    }

    private long countForReceiver(UUID eventId, Long receiverId) {
        return notifications(eventId).stream()
                .filter(notification -> receiverId.equals(notification.getReceiverId()))
                .count();
    }

    private void publishCommitted(OrderStatusChangedEvent event) {
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> eventPublisher.publishEvent(event));
    }

    private Fixture fixture(String prefix) {
        Long buyerId = insertUser("notification-" + prefix + "-buyer-"
                + UUID.randomUUID() + "@example.test", "USER");
        Long ownerId = insertUser("notification-" + prefix + "-owner-"
                + UUID.randomUUID() + "@example.test", "USER");
        Long shopId = insertShop(ownerId, "Notification " + prefix + " Shop");
        return new Fixture(buyerId, ownerId, shopId);
    }

    private OrderStatusChangedEvent event(Fixture fixture,
                                          Long actorId,
                                          String oldStatus,
                                          String newStatus) {
        return new OrderStatusChangedEvent(
                UUID.randomUUID(),
                ORDER_ID_SEQUENCE.incrementAndGet(),
                fixture.shopId(),
                fixture.buyerId(),
                actorId,
                "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                oldStatus,
                newStatus,
                Instant.now());
    }

    private Long insertUser(String email, String role) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Notification Integration User", email, "not-a-real-password-hash", role);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long insertShop(Long ownerId, String shopName) {
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   submitted_at, approved_at, created_at, updated_at)
                values (?, ?, ?, ?, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName, "0900000000", "Test address");
        return jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);
    }

    private record Fixture(Long buyerId, Long ownerId, Long shopId) {
    }

    private record StatusTransition(String oldStatus, String newStatus) {
    }
}
