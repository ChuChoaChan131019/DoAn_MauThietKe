package com.senvia.doangiuaky.shopping.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShoppingAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void guestCannotAddItemToCart() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .with(csrf())
                        .param("productId", "1")
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void adminCannotAddItemToCart() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("productId", "1")
                        .param("quantity", "1"))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestCannotAddFavorite() throws Exception {
        mockMvc.perform(post("/account/wishlist/1").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void adminCannotAddFavorite() throws Exception {
        mockMvc.perform(post("/account/wishlist/1")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void favoritePostRequiresCsrf() throws Exception {
        mockMvc.perform(post("/account/wishlist/1")
                        .with(user("buyer@example.com").roles("USER")))
                .andExpect(status().isForbidden());
    }
}
