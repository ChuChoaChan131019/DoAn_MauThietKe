package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.merchant.dto.AdminCategoryView;
import com.senvia.doangiuaky.merchant.service.CategoryDuplicateNameException;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AdminCategoryController.class)
@Import(SecurityConfig.class)
class AdminCategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CategoryManagementService categoryService;

    private IdentityPrincipal adminPrincipal;
    private IdentityPrincipal userPrincipal;

    @BeforeEach
    void setUpPrincipals() {
        adminPrincipal = principal(1L, "ROLE_ADMIN");
        userPrincipal = principal(2L, "ROLE_USER");
        when(categoryService.listAll(1L)).thenReturn(List.of(
                new AdminCategoryView(10L, "Nhà cửa", "Đồ dùng", true),
                new AdminCategoryView(11L, "Cũ", null, false)));
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/admin/categories"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(categoryService, never()).listAll(any());
    }

    @Test
    void userCannotViewCategories() throws Exception {
        mockMvc.perform(get("/admin/categories").with(user(userPrincipal)))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).listAll(any());
    }

    @Test
    void adminCanViewCategories() throws Exception {
        mockMvc.perform(get("/admin/categories").with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/admin-categories"))
                .andExpect(model().attribute("categories", List.of(
                        new AdminCategoryView(10L, "Nhà cửa", "Đồ dùng", true),
                        new AdminCategoryView(11L, "Cũ", null, false))))
                .andExpect(model().attributeExists("categoryForm", "editForm"));
    }

    @Test
    void adminCanCreateCategoryWithCsrf() throws Exception {
        mockMvc.perform(post("/admin/categories")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("name", "Nhà cửa")
                        .param("description", "Đồ dùng"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attribute("successMessage", "Đã thêm danh mục thành công."));

        verify(categoryService).create(eq(1L), any());
    }

    @Test
    void invalidCreateReturnsValidationErrors() throws Exception {
        mockMvc.perform(post("/admin/categories")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("name", "   "))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/admin-categories"))
                .andExpect(model().attributeHasFieldErrors("categoryForm", "name"));

        verify(categoryService, never()).create(any(), any());
    }

    @Test
    void duplicateCreateReturnsBusinessError() throws Exception {
        doThrow(new CategoryDuplicateNameException("Tên danh mục đã tồn tại."))
                .when(categoryService).create(eq(1L), any());

        mockMvc.perform(post("/admin/categories")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("name", "Nhà cửa"))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/admin-categories"))
                .andExpect(model().attributeHasFieldErrors("categoryForm", "name"));
    }

    @Test
    void adminCanUpdateAndHideCategoryWithCsrf() throws Exception {
        when(categoryService.getForAdmin(1L, 10L))
                .thenReturn(new AdminCategoryView(10L, "Nhà cửa", "Đồ dùng", true));

        mockMvc.perform(post("/admin/categories/10/update")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("name", "Nhà mới"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        mockMvc.perform(post("/admin/categories/10/hide")
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        verify(categoryService).update(eq(1L), eq(10L), any());
        verify(categoryService).hide(1L, 10L);
    }

    @Test
    void adminCannotMutateWithoutCsrf() throws Exception {
        mockMvc.perform(post("/admin/categories")
                        .with(user(adminPrincipal))
                        .param("name", "Nhà cửa"))
                .andExpect(status().isForbidden());

        verify(categoryService, never()).create(any(), any());
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
