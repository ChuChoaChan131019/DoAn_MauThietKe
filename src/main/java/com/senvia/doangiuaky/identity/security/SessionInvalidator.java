package com.senvia.doangiuaky.identity.security;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class SessionInvalidator {

    private final SessionRegistry sessionRegistry;

    public SessionInvalidator(SessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    public void invalidate(Long userId) {
        invalidate(userId, null, false);
    }

    public void invalidateOtherSessions(Long userId, String currentSessionId) {
        Objects.requireNonNull(currentSessionId, "currentSessionId");
        invalidate(userId, currentSessionId, true);
    }

    private void invalidate(Long userId, String excludedSessionId, boolean excludeCurrentSession) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            if (principal instanceof IdentityPrincipal identityPrincipal
                    && identityPrincipal.getUserId().equals(userId)) {
                for (SessionInformation session : sessionRegistry.getAllSessions(principal, false)) {
                    if (!excludeCurrentSession || !Objects.equals(excludedSessionId, session.getSessionId())) {
                        session.expireNow();
                    }
                }
            }
        }
    }
}
