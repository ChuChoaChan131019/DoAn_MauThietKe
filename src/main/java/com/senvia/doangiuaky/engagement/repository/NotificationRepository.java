package com.senvia.doangiuaky.engagement.repository;

import com.senvia.doangiuaky.engagement.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    long countByEventId(UUID eventId);

    List<Notification> findAllByEventIdOrderByReceiverIdAsc(UUID eventId);

    Page<Notification> findByReceiverId(Long receiverId, Pageable pageable);

    long countByReceiverIdAndReadFalse(Long receiverId);

    Optional<Notification> findByIdAndReceiverId(Long id, Long receiverId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification notification set notification.read = true "
            + "where notification.receiverId = :receiverId and notification.read = false")
    int markAllReadByReceiverId(@Param("receiverId") Long receiverId);
}
