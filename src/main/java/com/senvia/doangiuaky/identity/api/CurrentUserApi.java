package com.senvia.doangiuaky.identity.api;

/**
 * Resolves the current account from the authenticated Spring Security principal.
 * Callers must not substitute a client-provided user identifier.
 */
public interface CurrentUserApi {

    UserSummary requireCurrentUser();
}
