package com.senvia.doangiuaky.identity.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.identity.entity.User;
import com.senvia.doangiuaky.identity.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserApiServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesCurrentDatabaseUserFromAuthenticatedPrincipal() {
        UserRepository repository = mock(UserRepository.class);
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getFullName()).thenReturn("Buyer");
        when(user.getRole()).thenReturn(UserRole.USER);
        when(user.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(repository.findByEmailIgnoreCase("buyer@example.com")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("BUYER@example.com", null, "ROLE_USER"));

        UserSummary current = new CurrentUserApiService(repository).requireCurrentUser();

        assertEquals(new UserSummary(7L, "Buyer", UserRole.USER, AccountStatus.ACTIVE), current);
    }

    @Test
    void rejectsMissingOrUnknownPrincipal() {
        UserRepository repository = mock(UserRepository.class);
        CurrentUserApiService service = new CurrentUserApiService(repository);
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::requireCurrentUser);

        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("missing@example.com", null, "ROLE_USER"));
        when(repository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::requireCurrentUser);
    }
}
