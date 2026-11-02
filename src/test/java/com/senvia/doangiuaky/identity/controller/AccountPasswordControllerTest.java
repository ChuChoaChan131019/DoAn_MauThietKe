package com.senvia.doangiuaky.identity.controller;

import com.senvia.doangiuaky.identity.dto.ChangePasswordForm;
import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.identity.service.IdentityService;
import com.senvia.doangiuaky.identity.service.InvalidPasswordException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
class AccountPasswordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityService identityService;

    private IdentityPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = principal(7L);
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/account/change-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/account/change-password").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(identityService, never()).changePassword(any(), any());
    }

    @Test
    void formIsRenderedForLoggedInUser() throws Exception {
        mockMvc.perform(get("/account/change-password").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/change-password"))
                .andExpect(model().attributeExists("passwordForm"));
    }

    @Test
    void successfulChangeUsesPrincipalIdAndKeepsCurrentSession() throws Exception {
        MockHttpSession session = new MockHttpSession();

        mockMvc.perform(post("/account/change-password")
                        .session(session)
                        .param("userId", "99")
                        .param("currentPassword", "oldPassword1")
                        .param("newPassword", "newPassword1")
                        .param("confirmPassword", "newPassword1")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/change-password"))
                .andExpect(flash().attribute("success", "Mật khẩu đã được cập nhật."));

        ArgumentCaptor<ChangePasswordForm> form = ArgumentCaptor.forClass(ChangePasswordForm.class);
        verify(identityService).changePassword(eq(7L), form.capture());
        verify(identityService, never()).changePassword(eq(99L), any());
        assertEquals("oldPassword1", form.getValue().getCurrentPassword());
        assertEquals("newPassword1", form.getValue().getNewPassword());
        // Hành vi hiện tại: session của người đổi mật khẩu vẫn còn hiệu lực.
        assertFalse(session.isInvalid());
    }

    @Test
    void wrongCurrentPasswordShowsErrorWithoutEchoingPasswords() throws Exception {
        doThrow(new InvalidPasswordException("Mật khẩu hiện tại không đúng."))
                .when(identityService).changePassword(eq(7L), any(ChangePasswordForm.class));

        mockMvc.perform(post("/account/change-password")
                        .param("currentPassword", "wrongSecret1")
                        .param("newPassword", "newSecret123")
                        .param("confirmPassword", "newSecret123")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/change-password"))
                .andExpect(model().attribute("passwordError", "Mật khẩu hiện tại không đúng."))
                .andExpect(content().string(containsString("Mật khẩu hiện tại không đúng.")))
                .andExpect(content().string(not(containsString("wrongSecret1"))))
                .andExpect(content().string(not(containsString("newSecret123"))));
    }

    @Test
    void mismatchedConfirmationIsRejectedBeforeCallingService() throws Exception {
        mockMvc.perform(post("/account/change-password")
                        .param("currentPassword", "oldPassword1")
                        .param("newPassword", "newPassword1")
                        .param("confirmPassword", "different1")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/change-password"))
                .andExpect(model().attributeHasFieldErrors("passwordForm", "confirmPassword"));

        verify(identityService, never()).changePassword(any(), any());
    }

    @Test
    void shortOverlongAndMissingPasswordsAreRejected() throws Exception {
        mockMvc.perform(post("/account/change-password")
                        .param("currentPassword", "")
                        .param("newPassword", "short")
                        .param("confirmPassword", "short")
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordForm", "currentPassword", "newPassword"));

        String tooLong = "a".repeat(73);
        mockMvc.perform(post("/account/change-password")
                        .param("currentPassword", "oldPassword1")
                        .param("newPassword", tooLong)
                        .param("confirmPassword", tooLong)
                        .with(user(principal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasFieldErrors("passwordForm", "newPassword"));

        verify(identityService, never()).changePassword(any(), any());
    }

    @Test
    void passwordChangeRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/account/change-password")
                        .param("currentPassword", "oldPassword1")
                        .param("newPassword", "newPassword1")
                        .param("confirmPassword", "newPassword1")
                        .with(user(principal)))
                .andExpect(status().isForbidden());

        verify(identityService, never()).changePassword(any(), any());
    }

    private static IdentityPrincipal principal(Long userId) {
        IdentityPrincipal principal = mock(IdentityPrincipal.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getUsername()).thenReturn("user" + userId + "@example.test");
        when(principal.getPassword()).thenReturn("password");
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getAuthorities())
                .thenAnswer(ignored -> List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return principal;
    }
}
