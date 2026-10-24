package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.dto.PublicShopView;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import com.senvia.doangiuaky.merchant.service.ShopProfileAuthorizationException;
import com.senvia.doangiuaky.merchant.service.ShopProfileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(SellerController.class)
@Import(SecurityConfig.class)
class SellerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShopProfileService shopProfileService;

    @MockitoBean
    private DemoMerchantApi catalogService;

    private IdentityPrincipal ownerPrincipal;

    @BeforeEach
    void setUp() {
        ownerPrincipal = mock(IdentityPrincipal.class);
        when(ownerPrincipal.getUserId()).thenReturn(1L);
        when(ownerPrincipal.getAuthorities()).thenAnswer(
                ignored -> List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(ownerPrincipal.getUsername()).thenReturn("owner@example.com");
        when(ownerPrincipal.isAccountNonExpired()).thenReturn(true);
        when(ownerPrincipal.isAccountNonLocked()).thenReturn(true);
        when(ownerPrincipal.isCredentialsNonExpired()).thenReturn(true);
        when(ownerPrincipal.isEnabled()).thenReturn(true);
    }

    @Test
    void ownerCanOpenApprovedShopProfile() throws Exception {
        when(shopProfileService.getOwnedApprovedShop(1L)).thenReturn(approvedShop());

        mockMvc.perform(get("/merchant/profile").with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/shop-info"));
    }

    @Test
    void merchantDashboardRedirectsToEngagementStatistics() throws Exception {
        mockMvc.perform(get("/merchant/dashboard").with(user(ownerPrincipal)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/dashboard"));
    }

    @Test
    void ownerCanSubmitShopProfileWithReplacementLogo() throws Exception {
        when(shopProfileService.getOwnedApprovedShop(1L)).thenReturn(approvedShop());

        mockMvc.perform(multipart("/merchant/profile")
                        .file(new MockMultipartFile("logo", "logo.png", "image/png", new byte[]{1, 2, 3}))
                        .with(user(ownerPrincipal))
                        .with(csrf())
                        .param("shopName", "Updated shop")
                        .param("description", "Updated description")
                        .param("phone", "0900000000")
                        .param("address", "Updated address"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merchant/profile"));

        verify(shopProfileService).update(eq(1L), any());
    }

    @Test
    void nonApprovedOwnerCannotOpenShopProfile() throws Exception {
        when(shopProfileService.getOwnedApprovedShop(1L))
                .thenThrow(new ShopProfileAuthorizationException("Shop chưa được duyệt."));

        mockMvc.perform(get("/merchant/profile").with(user(ownerPrincipal)))
                .andExpect(status().isForbidden());

        verify(shopProfileService, never()).update(any(), any());
    }

    @Test
    void anonymousBuyerCanOpenApprovedPublicShop() throws Exception {
        PublicShopView shop = new PublicShopView(10L, "NOLA Store", "Mô tả", null, "Địa chỉ");
        when(catalogService.findPublicShop(10L)).thenReturn(Optional.of(shop));
        when(catalogService.findSaleableCatalogProductsByShop(10L)).thenReturn(List.of());

        mockMvc.perform(get("/merchant/shop-public").param("shopId", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/shop-public"))
                .andExpect(model().attribute("shop", shop))
                .andExpect(model().attribute("products", List.of()));
    }

    @Test
    void unavailablePublicShopReturnsNotFound() throws Exception {
        when(catalogService.findPublicShop(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/merchant/shop-public").param("shopId", "99"))
                .andExpect(status().isNotFound());
    }

    private static Shop approvedShop() {
        Shop shop = Shop.createPending(1L, "Shop", "Description",
                "https://cloudinary.test/logo.png", "shops/1/logo", "0900000000", "Address");
        shop.approve(99L, Instant.now());
        return shop;
    }
}
