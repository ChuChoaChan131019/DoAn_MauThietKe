package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.service.NotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class NotificationHeaderModelAdvice {

    private final ObjectProvider<NotificationService> notificationServiceProvider;

    public NotificationHeaderModelAdvice(ObjectProvider<NotificationService> notificationServiceProvider) {
        this.notificationServiceProvider = notificationServiceProvider;
    }

    @ModelAttribute("unreadNotifications")
    public Long unreadNotifications(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        NotificationService notificationService = notificationServiceProvider.getIfAvailable();
        if (notificationService == null) {
            return null;
        }
        try {
            return notificationService.getUnreadCount();
        } catch (AuthenticationException exception) {
            return 0L;
        }
    }
}
