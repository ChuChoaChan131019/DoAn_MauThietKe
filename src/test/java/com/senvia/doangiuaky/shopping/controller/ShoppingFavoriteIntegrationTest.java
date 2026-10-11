package com.senvia.doangiuaky.shopping.controller;

import com.senvia.doangiuaky.shopping.service.FavoriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class ShoppingFavoriteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FavoriteService favoriteService;

    @Test
    void userCanAddListAndRemoveFavoriteWithoutDuplicates() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String buyerEmail = "favorite-buyer-" + suffix + "@example.com";
        String otherBuyerEmail = "favorite-other-" + suffix + "@example.com";
        insertUser(buyerEmail);
        insertUser(otherBuyerEmail);
        Long productId = insertSaleableProductFixture(suffix);
        insertFavorite(otherBuyerEmail, productId);

        mockMvc.perform(get("/products/{productId}", productId)
                        .with(user(buyerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action=\"/account/wishlist/" + productId + "\"")));

        mockMvc.perform(get("/products")
                        .param("q", "Favorite Integration Product " + suffix)
                        .with(user(buyerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "action=\"/account/wishlist/" + productId + "\"")));

        mockMvc.perform(post("/account/wishlist/{productId}", productId)
                        .with(user(buyerEmail).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/wishlist"));
        mockMvc.perform(post("/account/wishlist/{productId}", productId)
                        .with(user(buyerEmail).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/wishlist"));

        assertEquals(1, favoriteCount(buyerEmail, productId));

        mockMvc.perform(get("/account/wishlist")
                        .with(user(buyerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("shopping/wishlist"))
                .andExpect(model().attributeExists("favorites"));

        mockMvc.perform(post("/account/wishlist/{productId}/remove", productId)
                        .with(user(buyerEmail).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/wishlist"));

        assertEquals(0, favoriteCount(buyerEmail, productId));
        assertEquals(1, favoriteCount(otherBuyerEmail, productId));
    }

    @Test
    void concurrentAddIsIdempotent() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String buyerEmail = "favorite-concurrent-" + suffix + "@example.com";
        insertUser(buyerEmail);
        Long buyerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, buyerEmail);
        Long productId = insertSaleableProductFixture("concurrent-" + suffix);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> addWhenReleased(buyerId, productId, ready, start));
            Future<?> second = executor.submit(() -> addWhenReleased(buyerId, productId, ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, favoriteCount(buyerEmail, productId));
    }

    private void addWhenReleased(Long buyerId, Long productId, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            favoriteService.addFavorite(buyerId, productId);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent favorite test was interrupted.", exception);
        }
    }

    private int favoriteCount(String email, Long productId) {
        Integer count = jdbcTemplate.queryForObject("""
                select count(*)
                from favorites f
                join users u on u.id = f.user_id
                where u.email = ? and f.product_id = ?
                """, Integer.class, email, productId);
        return count == null ? 0 : count;
    }

    private void insertFavorite(String email, Long productId) {
        jdbcTemplate.update("""
                insert into favorites (user_id, product_id, created_at)
                select id, ?, CURRENT_TIMESTAMP from users where email = ?
                """, productId, email);
    }

    private Long insertSaleableProductFixture(String suffix) {
        String ownerEmail = "favorite-owner-" + suffix + "@example.com";
        insertUser(ownerEmail);
        Long ownerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, ownerEmail);

        String shopName = "Favorite Integration Shop " + suffix;
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   submitted_at, approved_at, created_at, updated_at)
                values (?, ?, '0900000000', 'Test address', 'APPROVED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName);
        Long shopId = jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);

        String categoryName = "Favorite Integration Category " + suffix;
        jdbcTemplate.update("""
                insert into categories (name, description, active, created_at, updated_at)
                values (?, 'Test category', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, categoryName);
        Long categoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = ?", Long.class, categoryName);

        String productName = "Favorite Integration Product " + suffix;
        jdbcTemplate.update("""
                insert into products (shop_id, category_id, name, description, price,
                                      stock_quantity, status, created_at, updated_at)
                values (?, ?, ?, 'Test product', 100000.00, 5, 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, shopId, categoryId, productName);
        return jdbcTemplate.queryForObject(
                "select id from products where shop_id = ? and name = ?",
                Long.class, shopId, productName);
    }

    private void insertUser(String email) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status,
                                   created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Favorite Integration User", email, "not-a-real-password-hash");
    }
}
