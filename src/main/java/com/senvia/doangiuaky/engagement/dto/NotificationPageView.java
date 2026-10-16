package com.senvia.doangiuaky.engagement.dto;

import java.util.List;

public record NotificationPageView(
        List<NotificationListItem> items,
        int currentPage,
        int totalPages,
        long totalItems,
        long unreadCount) {

    public boolean hasPrevious() {
        return currentPage > 1;
    }

    public boolean hasNext() {
        return currentPage < totalPages;
    }
}
