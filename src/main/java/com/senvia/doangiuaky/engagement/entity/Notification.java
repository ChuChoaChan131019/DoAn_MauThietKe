package com.senvia.doangiuaky.engagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "notifications", uniqueConstraints = @UniqueConstraint(
        name = "uk_notifications_event_receiver", columnNames = {"event_id", "receiver_id"}))
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    private Notification(UUID eventId, Long receiverId, NotificationType type,
                         String title, String content, Long referenceId) {
        this.eventId = Objects.requireNonNull(eventId, "Event ID must not be null");
        this.receiverId = Objects.requireNonNull(receiverId, "Receiver ID must not be null");
        this.type = Objects.requireNonNull(type, "Notification type must not be null");
        this.title = Objects.requireNonNull(title, "Notification title must not be null");
        this.content = Objects.requireNonNull(content, "Notification content must not be null");
        this.referenceId = referenceId;
    }

    public static Notification create(UUID eventId, Long receiverId, NotificationType type,
                                      String title, String content, Long referenceId) {
        return new Notification(eventId, receiverId, type, title, content, referenceId);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public UUID getEventId() {
        return eventId;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public NotificationType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public boolean isRead() {
        return read;
    }

    public boolean markRead() {
        if (read) {
            return false;
        }
        read = true;
        return true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
