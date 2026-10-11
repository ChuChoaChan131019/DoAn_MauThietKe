package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.merchant.dto.ShopResubmissionForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.service.ShopResubmissionException;
import com.senvia.doangiuaky.merchant.service.ShopResubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ShopResubmissionController.class)
@Import(SecurityConfig.class)
class ShopResubmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopResubmissionService resubmissionService;

    private IdentityPrincipal userPrincipal;

    @BeforeEach
    void setUpPrincipal() {
        userPrincipal = principal(12L, "ROLE_USER");
    }

    @Test
    void anonymousUserIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/merchant/shop-resubmission"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void adminCannotOpenResubmissionForm() throws Exception {
        mockMvc.perform(get("/merchant/shop-resubmission")
                        .with(user(principal(1L, "ROLE_ADMIN"))))
                .andExpect(status().isForbidden());

        verify(resubmissionService, never()).getRejectedShop(any());
    }

    @Test
    void rejectedOwnerCanOpenPrefilledResubmissionForm() throws Exception {
        Shop shop = mock(Shop.class);
        when(shop.getShopName()).thenReturn("Old shop");
        when(shop.getDescription()).thenReturn("Old description");
        when(shop.getPhone()).thenReturn("0900000000");
        when(shop.getAddress()).thenReturn("Old address");
        when(resubmissionService.getRejectedShop(12L)).thenReturn(shop);

        mockMvc.perform(get("/merchant/shop-resubmission").with(user(userPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/shop-resubmission"))
                .andExpect(model().attributeExists("ownedShop"))
                .andExpect(model().attributeExists("shopResubmissionForm"));
    }

    @Test
    void validResubmissionRedirectsToStatusWithSuccessMessage() throws Exception {
        mockMvc.perform(multipart("/merchant/shop-resubmission")
                        .param("shopName", "Updated shop")
                        .param("description", "Updated description")
                        .param("phone", "0900000012")
                        .param("address", "Updated address")
                        .with(user(userPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merchant/shop-status"))
                .andExpect(flash().attributeExists("success"));

        verify(resubmissionService).resubmit(eq(12L), any(ShopResubmissionForm.class));
    }

    @Test
    void validationFailureReturnsFormAndDoesNotResubmit() throws Exception {
        Shop shop = mock(Shop.class);
        when(resubmissionService.getRejectedShop(12L)).thenReturn(shop);

        mockMvc.perform(multipart("/merchant/shop-resubmission")
                        .param("shopName", "")
                        .param("description", "")
                        .param("phone", "invalid")
                        .param("address", "")
                        .with(user(userPrincipal))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/shop-resubmission"))
                .andExpect(model().attributeHasFieldErrors(
                        "shopResubmissionForm", "shopName", "description", "phone", "address"));

        verify(resubmissionService, never()).resubmit(any(), any());
    }

    @Test
    void submissionWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(multipart("/merchant/shop-resubmission")
                        .file(new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1}))
                        .param("shopName", "Updated shop")
                        .param("description", "Updated description")
                        .param("phone", "0900000012")
                        .param("address", "Updated address")
                        .with(user(userPrincipal)))
                .andExpect(status().isForbidden());

        verify(resubmissionService, never()).resubmit(any(), any());
    }

    @Test
    void nonRejectedShopIsRedirectedToStatusWithError() throws Exception {
        when(resubmissionService.getRejectedShop(12L))
                .thenThrow(new ShopResubmissionException("Chỉ yêu cầu bị từ chối mới được gửi lại."));

        mockMvc.perform(get("/merchant/shop-resubmission").with(user(userPrincipal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merchant/shop-status"))
                .andExpect(flash().attributeExists("error"));
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
