package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationHeaderModelAdviceTest {

    @Test
    void anonymousRequestDoesNotQueryUnreadCount() {
        NotificationService service = mock(NotificationService.class);
        ObjectProvider<NotificationService> provider = provider(service);
        var advice = new NotificationHeaderModelAdvice(provider);
        var anonymous = new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));

        assertNull(advice.unreadNotifications(anonymous));
        verify(service, never()).getUnreadCount();
    }

    @Test
    void authenticatedRequestUsesRealUnreadCount() {
        NotificationService service = mock(NotificationService.class);
        when(service.getUnreadCount()).thenReturn(4L);
        var advice = new NotificationHeaderModelAdvice(provider(service));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "receiver", "password", AuthorityUtils.createAuthorityList("ROLE_USER"));

        assertEquals(4L, advice.unreadNotifications(authentication));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<NotificationService> provider(NotificationService service) {
        ObjectProvider<NotificationService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(service);
        return provider;
    }
}
