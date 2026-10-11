package com.senvia.doangiuaky.shopping.controller;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.shopping.dto.AddCartItemResult;
import com.senvia.doangiuaky.shopping.dto.CartItemView;
import com.senvia.doangiuaky.shopping.dto.FavoriteItemView;
import com.senvia.doangiuaky.shopping.service.CartService;
import com.senvia.doangiuaky.shopping.service.FavoriteService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

class ShoppingControllerTest {

    @Test
    void invalidQuantityIsRejectedBeforeServiceCall() throws Exception {
        CartService cartService = mock(CartService.class);
        FavoriteService favoriteService = mock(FavoriteService.class);
        IdentityApi identityApi = mock(IdentityApi.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ShoppingController(cartService, favoriteService, identityApi)).build();

        mvc.perform(post("/cart/items")
                        .principal(principal("buyer@example.com"))
                        .param("productId", "20")
                        .param("quantity", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService, never()).addItem(eq(10L), eq(20L), eq(0));
    }

    @Test
    void validPostResolvesCurrentUserFromIdentityApiAndRedirects() throws Exception {
        CartService cartService = mock(CartService.class);
        FavoriteService favoriteService = mock(FavoriteService.class);
        IdentityApi identityApi = mock(IdentityApi.class);
        when(identityApi.findUserByEmail("buyer@example.com"))
                .thenReturn(java.util.Optional.of(new UserSummary(10L, "Buyer", UserRole.USER, AccountStatus.ACTIVE)));
        when(cartService.addItem(10L, 20L, 2)).thenReturn(new AddCartItemResult(
                new CartItemView(20L, 30L, "Shop", "Product", null,
                        new BigDecimal("100000"), 5, 2, new BigDecimal("200000"), true, null),
                false,
                "Đã thêm sản phẩm vào giỏ hàng."));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ShoppingController(cartService, favoriteService, identityApi)).build();

        mvc.perform(post("/cart/items")
                        .principal(principal("buyer@example.com"))
                        .param("productId", "20")
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        verify(cartService).addItem(10L, 20L, 2);
    }

    @Test
    void wishlistLoadsOnlyCurrentUsersFavorites() throws Exception {
        CartService cartService = mock(CartService.class);
        FavoriteService favoriteService = mock(FavoriteService.class);
        IdentityApi identityApi = mock(IdentityApi.class);
        when(identityApi.findUserByEmail("buyer@example.com"))
                .thenReturn(java.util.Optional.of(new UserSummary(10L, "Buyer", UserRole.USER, AccountStatus.ACTIVE)));
        List<FavoriteItemView> favorites = List.of(new FavoriteItemView(
                1L, 20L, "Product", null, new BigDecimal("100000"), "Shop",
                true, null, Instant.parse("2026-10-10T00:00:00Z")));
        when(favoriteService.listForUser(10L)).thenReturn(favorites);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ShoppingController(cartService, favoriteService, identityApi)).build();

        mvc.perform(get("/account/wishlist").principal(principal("buyer@example.com")))
                .andExpect(status().isOk())
                .andExpect(view().name("shopping/wishlist"))
                .andExpect(model().attribute("favorites", favorites));

        verify(favoriteService).listForUser(10L);
    }

    @Test
    void addAndRemoveFavoriteResolveCurrentUserWithoutAcceptingUserId() throws Exception {
        CartService cartService = mock(CartService.class);
        FavoriteService favoriteService = mock(FavoriteService.class);
        IdentityApi identityApi = mock(IdentityApi.class);
        when(identityApi.findUserByEmail("buyer@example.com"))
                .thenReturn(java.util.Optional.of(new UserSummary(10L, "Buyer", UserRole.USER, AccountStatus.ACTIVE)));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new ShoppingController(cartService, favoriteService, identityApi)).build();

        mvc.perform(post("/account/wishlist/20").principal(principal("buyer@example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/wishlist"))
                .andExpect(flash().attribute("favoriteSuccess", "Đã thêm sản phẩm vào yêu thích."));
        mvc.perform(post("/account/wishlist/20/remove").principal(principal("buyer@example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/wishlist"))
                .andExpect(flash().attribute("favoriteSuccess", "Đã xóa sản phẩm khỏi yêu thích."));

        verify(favoriteService).addFavorite(10L, 20L);
        verify(favoriteService).removeFavorite(10L, 20L);
    }

    private static Principal principal(String name) {
        return () -> name;
    }
}
