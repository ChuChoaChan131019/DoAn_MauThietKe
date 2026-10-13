package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.merchant.dto.AdminShopDetail;
import com.senvia.doangiuaky.merchant.dto.AdminShopSummary;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.service.AdminShopManagementService;
import com.senvia.doangiuaky.merchant.service.ShopLockService;
import com.senvia.doangiuaky.merchant.service.ShopManagementNotFoundException;
import com.senvia.doangiuaky.merchant.state.InvalidShopStateTransitionException;
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

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminShopManagementController.class)
@Import(SecurityConfig.class)
class AdminShopManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminShopManagementService managementService;

    @MockitoBean
    private ShopLockService lockService;

    private IdentityPrincipal adminPrincipal;
    private IdentityPrincipal userPrincipal;

    @BeforeEach
    void setUpPrincipals() {
        adminPrincipal = principal(1L, "ROLE_ADMIN");
        userPrincipal = principal(2L, "ROLE_USER");
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/shops"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(managementService, never()).listManagedShops(1L);
    }

    @Test
    void regularUserCannotManageShops() throws Exception {
        mockMvc.perform(get("/admin/shops").with(user(userPrincipal)))
                .andExpect(status().isForbidden());

        verify(managementService, never()).listManagedShops(2L);
    }

    @Test
    void adminCanViewManagedShopListWithStatusCounts() throws Exception {
        var approved = new AdminShopSummary(10L, "A", "Owner A", ShopStatus.APPROVED,
                Instant.parse("2026-10-10T10:00:00Z"), null);
        var locked = new AdminShopSummary(11L, "B", "Owner B", ShopStatus.LOCKED,
                Instant.parse("2026-10-10T10:00:00Z"), Instant.parse("2026-10-11T10:00:00Z"));
        when(managementService.listManagedShops(1L)).thenReturn(List.of(approved, locked));

        mockMvc.perform(get("/admin/shops").with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/admin/shops"))
                .andExpect(model().attribute("approvedCount", 1L))
                .andExpect(model().attribute("lockedCount", 1L));
    }

    @Test
    void adminCanViewShopDetail() throws Exception {
        AdminShopDetail detail = detail(ShopStatus.APPROVED);
        when(managementService.getManagedShop(1L, 10L)).thenReturn(detail);

        mockMvc.perform(get("/admin/shops/10").with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/admin/shop-detail"))
                .andExpect(model().attribute("shop", detail))
                .andExpect(model().attributeExists("shopLockForm"));
    }

    @Test
    void adminCanLockShopWithCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/shops/10/lock")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("reason", "  Vi phạm chính sách  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shops/10"))
                .andExpect(flash().attribute("successMessage", "Đã khóa gian hàng thành công."));

        verify(lockService).lock(10L, 1L, "  Vi phạm chính sách  ");
    }

    @Test
    void blankLockReasonRendersDetailWithValidationError() throws Exception {
        AdminShopDetail detail = detail(ShopStatus.APPROVED);
        when(managementService.getManagedShop(1L, 10L)).thenReturn(detail);

        mockMvc.perform(post("/admin/shops/10/lock")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("reason", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/admin/shop-detail"))
                .andExpect(model().attribute("shop", detail))
                .andExpect(model().attributeHasFieldErrors("shopLockForm", "reason"));

        verify(lockService, never()).lock(10L, 1L, "   ");
    }

    @Test
    void adminCanUnlockShopWithCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/shops/10/unlock")
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shops/10"))
                .andExpect(flash().attribute("successMessage", "Đã mở khóa gian hàng thành công."));

        verify(lockService).unlock(10L, 1L);
    }

    @Test
    void adminCannotMutateShopWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/admin/shops/10/unlock").with(user(adminPrincipal)))
                .andExpect(status().isForbidden());

        verify(lockService, never()).unlock(10L, 1L);
    }

    @Test
    void invalidTransitionRedirectsWithClearMessage() throws Exception {
        doThrow(new InvalidShopStateTransitionException(ShopStatus.LOCKED, ShopStatus.LOCKED))
                .when(lockService).lock(10L, 1L, "Reason");

        mockMvc.perform(post("/admin/shops/10/lock")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("reason", "Reason"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shops/10"))
                .andExpect(flash().attribute("errorMessage", "Chỉ gian hàng đang hoạt động mới được khóa."));
    }

    @Test
    void missingShopReturnsNotFound() throws Exception {
        when(managementService.getManagedShop(1L, 99L))
                .thenThrow(new ShopManagementNotFoundException("Không tìm thấy gian hàng cần quản lý."));

        mockMvc.perform(get("/admin/shops/99").with(user(adminPrincipal)))
                .andExpect(status().isNotFound());
    }

    private static AdminShopDetail detail(ShopStatus status) {
        return new AdminShopDetail(
                10L, "Shop A", "Description", null, "0900000000", "Address",
                "Owner", status, "Approver", Instant.parse("2026-10-10T10:00:00Z"),
                status == ShopStatus.LOCKED ? "Reason" : null,
                status == ShopStatus.LOCKED ? "Admin" : null,
                status == ShopStatus.LOCKED ? Instant.parse("2026-10-11T10:00:00Z") : null);
    }

    private static IdentityPrincipal principal(Long userId, String authority) {
        IdentityPrincipal principal = mock(IdentityPrincipal.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getUsername()).thenReturn("user" + userId + "@example.com");
        when(principal.getPassword()).thenReturn("password");
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getAuthorities()).thenAnswer(ignored ->
                List.of(new SimpleGrantedAuthority(authority)));
        return principal;
    }
}
