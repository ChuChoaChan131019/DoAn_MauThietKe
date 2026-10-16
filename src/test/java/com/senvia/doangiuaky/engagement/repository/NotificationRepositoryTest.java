package com.senvia.doangiuaky.engagement.repository;

import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsUnreadNotificationAndAllowsOneEventForDifferentReceivers() {
        Long firstReceiverId = insertUser("notification-first-" + UUID.randomUUID() + "@example.test", "ADMIN");
        Long secondReceiverId = insertUser("notification-second-" + UUID.randomUUID() + "@example.test", "ADMIN");
        UUID eventId = UUID.randomUUID();

        Notification first = notificationRepository.saveAndFlush(notification(
                eventId, firstReceiverId, 41L));
        Notification second = notificationRepository.saveAndFlush(notification(
                eventId, secondReceiverId, 41L));

        assertNotNull(first.getId());
        assertNotNull(first.getCreatedAt());
        assertFalse(first.isRead());
        assertEquals(NotificationType.SHOP_REQUESTED, first.getType());
        assertEquals(41L, first.getReferenceId());
        assertEquals(2, notificationRepository.countByEventId(eventId));
        assertNotNull(second.getId());
    }

    @Test
    void uniqueConstraintRejectsTheSameEventAndReceiverPair() {
        Long receiverId = insertUser("notification-duplicate-" + UUID.randomUUID() + "@example.test", "ADMIN");
        UUID eventId = UUID.randomUUID();
        notificationRepository.saveAndFlush(notification(eventId, receiverId, 51L));

        assertThrows(DataIntegrityViolationException.class,
                () -> notificationRepository.saveAndFlush(notification(eventId, receiverId, 51L)));
    }

    @Test
    void allowsDifferentEventsForTheSameReceiver() {
        Long receiverId = insertUser("notification-events-" + UUID.randomUUID() + "@example.test", "ADMIN");

        notificationRepository.saveAndFlush(notification(UUID.randomUUID(), receiverId, 61L));
        notificationRepository.saveAndFlush(notification(UUID.randomUUID(), receiverId, 62L));

        assertEquals(2L, jdbcTemplate.queryForObject(
                "select count(*) from notifications where receiver_id = ? and reference_id in (61, 62)",
                Long.class, receiverId));
    }

    @Test
    void databaseSuppliesUnreadAndCreatedAtDefaults() {
        Long receiverId = insertUser("notification-defaults-" + UUID.randomUUID() + "@example.test", "ADMIN");
        UUID eventId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into notifications (event_id, receiver_id, type, title, content, reference_id)
                values (?, ?, 'SHOP_REQUESTED', ?, ?, ?)
                """, eventId, receiverId, "Yêu cầu mở gian hàng mới",
                "Một gian hàng vừa gửi yêu cầu mở gian hàng.", 71L);

        Notification saved = notificationRepository.findAllByEventIdOrderByReceiverIdAsc(eventId).getFirst();
        assertFalse(saved.isRead());
        assertNotNull(saved.getCreatedAt());
    }

    private Notification notification(UUID eventId, Long receiverId, Long referenceId) {
        return Notification.create(
                eventId,
                receiverId,
                NotificationType.SHOP_REQUESTED,
                "Yêu cầu mở gian hàng mới",
                "Một gian hàng vừa gửi yêu cầu mở gian hàng.",
                referenceId);
    }

    private Long insertUser(String email, String role) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Notification Receiver", email, "not-a-real-password-hash", role);
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }
}
