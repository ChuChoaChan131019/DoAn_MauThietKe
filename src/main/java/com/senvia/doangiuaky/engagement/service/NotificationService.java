package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.dto.NotificationListItem;
import com.senvia.doangiuaky.engagement.dto.NotificationPageView;
import com.senvia.doangiuaky.engagement.entity.Notification;
import com.senvia.doangiuaky.engagement.repository.NotificationRepository;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    static final int PAGE_SIZE = 10;
    private static final Sort NEWEST_FIRST = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final NotificationRepository notificationRepository;
    private final CurrentUserApi currentUserApi;

    public NotificationService(NotificationRepository notificationRepository, CurrentUserApi currentUserApi) {
        this.notificationRepository = notificationRepository;
        this.currentUserApi = currentUserApi;
    }

    @Transactional(readOnly = true)
    public NotificationPageView getNotifications(int requestedPage) {
        Long receiverId = currentReceiverId();
        int pageIndex = Math.max(requestedPage, 1) - 1;
        Page<Notification> page = findPage(receiverId, pageIndex);

        if (page.getTotalPages() > 0 && pageIndex >= page.getTotalPages()) {
            page = findPage(receiverId, page.getTotalPages() - 1);
        }

        return new NotificationPageView(
                page.map(this::toListItem).getContent(),
                page.getTotalPages() == 0 ? 1 : page.getNumber() + 1,
                page.getTotalPages(),
                page.getTotalElements(),
                notificationRepository.countByReceiverIdAndReadFalse(receiverId));
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository.countByReceiverIdAndReadFalse(currentReceiverId());
    }

    @Transactional
    public boolean markRead(Long notificationId) {
        Long receiverId = currentReceiverId();
        Notification notification = notificationRepository.findByIdAndReceiverId(notificationId, receiverId)
                .orElseThrow(NotificationNotFoundException::new);
        return notification.markRead();
    }

    @Transactional
    public int markAllRead() {
        return notificationRepository.markAllReadByReceiverId(currentReceiverId());
    }

    private Page<Notification> findPage(Long receiverId, int pageIndex) {
        return notificationRepository.findByReceiverId(
                receiverId, PageRequest.of(pageIndex, PAGE_SIZE, NEWEST_FIRST));
    }

    private Long currentReceiverId() {
        return currentUserApi.requireCurrentUser().userId();
    }

    private NotificationListItem toListItem(Notification notification) {
        return new NotificationListItem(
                notification.getId(), notification.getType(), notification.getTitle(),
                notification.getContent(), notification.isRead(), notification.getCreatedAt());
    }
}
