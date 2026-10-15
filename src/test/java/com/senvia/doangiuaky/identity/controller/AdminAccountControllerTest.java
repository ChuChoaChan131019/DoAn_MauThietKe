package com.senvia.doangiuaky.identity.controller;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.dto.AdminAccountView;
import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.identity.service.AccountOperationException;
import com.senvia.doangiuaky.identity.service.AdminAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
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

@WebMvcTest(AdminAccountController.class)
@Import(SecurityConfig.class)
class AdminAccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminAccountService adminAccountService;

    private IdentityPrincipal adminPrincipal;
    private IdentityPrincipal userPrincipal;

    @BeforeEach
    void setUpPrincipals() {
        adminPrincipal = principal(1L, "ROLE_ADMIN");
        userPrincipal = principal(2L, "ROLE_USER");
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/accounts"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(adminAccountService, never()).search(null, null);
    }

    @Test
    void legacyIdentityUrlRedirectsToPublicAdminAccountsPath() throws Exception {
        mockMvc.perform(get("/identity/admin/accounts").with(user(adminPrincipal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts"));
    }

    @Test
    void legacyIdentityDetailUrlRedirectsToPublicAdminAccountsPath() throws Exception {
        mockMvc.perform(get("/identity/admin/accounts/10").with(user(adminPrincipal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/10"));
    }

    @Test
    void regularUserCannotViewAccounts() throws Exception {
        mockMvc.perform(get("/admin/accounts").with(user(userPrincipal)))
                .andExpect(status().isForbidden());

        verify(adminAccountService, never()).search(null, null);
    }

    @Test
    void adminCanSearchAndFilterAccounts() throws Exception {
        when(adminAccountService.search("Test", AccountStatus.LOCKED)).thenReturn(List.of());

        mockMvc.perform(get("/admin/accounts")
                        .with(user(adminPrincipal))
                        .param("query", "Test")
                        .param("status", "LOCKED"))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/admin/accounts"))
                .andExpect(model().attribute("query", "Test"))
                .andExpect(model().attribute("selectedStatus", AccountStatus.LOCKED))
                .andExpect(model().attribute("currentAdminId", 1L));

        verify(adminAccountService).search("Test", AccountStatus.LOCKED);
    }

    @Test
    void adminCanViewAccountDetailsInModal() throws Exception {
        AdminAccountView account = lockedUser();
        when(adminAccountService.getAccount(10L)).thenReturn(account);
        when(adminAccountService.search(null, null)).thenReturn(List.of(account));

        mockMvc.perform(get("/admin/accounts/10").with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("identity/admin/accounts"))
                .andExpect(model().attribute("selectedAccount", account))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("accountDetailModal")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chi tiết tài khoản")));
    }

    @Test
    void missingAccountDetailsReturnNotFound() throws Exception {
        when(adminAccountService.getAccount(99L))
                .thenThrow(new AccountOperationException("Tài khoản không tồn tại."));

        mockMvc.perform(get("/admin/accounts/99").with(user(adminPrincipal)))
                .andExpect(status().isNotFound());
    }

    @Test
    void adminCanLockUserWithCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/accounts/10/lock")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("reason", "Vi phạm chính sách"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/10"))
                .andExpect(flash().attribute("success", "Tài khoản đã được khóa."));

        verify(adminAccountService).lock(10L, 1L, "Vi phạm chính sách");
    }

    @Test
    void lockValidationFailureReturnsToDetailsWithErrorMessage() throws Exception {
        doThrow(new AccountOperationException("Lý do khóa phải từ 1 đến 500 ký tự."))
                .when(adminAccountService).lock(10L, 1L, "   ");

        mockMvc.perform(post("/admin/accounts/10/lock")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("reason", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/10"))
                .andExpect(flash().attribute("error", "Lý do khóa phải từ 1 đến 500 ký tự."));
    }

    @Test
    void adminCanUnlockUserAndPassesActorId() throws Exception {
        mockMvc.perform(post("/admin/accounts/10/unlock")
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/10"))
                .andExpect(flash().attribute("success", "Tài khoản đã được mở khóa."));

        verify(adminAccountService).unlock(10L, 1L);
    }

    @Test
    void accountMutationRequiresCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/accounts/10/unlock").with(user(adminPrincipal)))
                .andExpect(status().isForbidden());

        verify(adminAccountService, never()).unlock(10L, 1L);
    }

    @Test
    void regularUserCannotLockAccount() throws Exception {
        mockMvc.perform(post("/admin/accounts/10/lock")
                        .with(user(userPrincipal))
                        .with(csrf())
                        .param("reason", "Vi phạm chính sách"))
                .andExpect(status().isForbidden());

        verify(adminAccountService, never()).lock(10L, 2L, "Vi phạm chính sách");
    }

    private static AdminAccountView lockedUser() {
        return new AdminAccountView(
                10L,
                "Người dùng thử",
                "test-user@example.test",
                UserRole.USER,
                AccountStatus.LOCKED,
                "Vi phạm chính sách",
                "Quản trị viên",
                Instant.parse("2026-10-15T10:00:00Z"));
    }

    private static IdentityPrincipal principal(Long userId, String authority) {
        IdentityPrincipal principal = mock(IdentityPrincipal.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getUsername()).thenReturn("user" + userId + "@example.test");
        when(principal.getPassword()).thenReturn("password");
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getAuthorities()).thenAnswer(ignored -> List.of(new SimpleGrantedAuthority(authority)));
        return principal;
    }
}
