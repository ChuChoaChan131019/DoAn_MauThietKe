package com.senvia.doangiuaky.shopping.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShoppingCartIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void userCanAddDatabaseCatalogProductWithoutForeignKeyError() throws Exception {
        String email = "cart-integration-" + UUID.randomUUID() + "@example.com";
        insertUser(email);
        Long productId = insertSaleableProductFixture();

        mockMvc.perform(post("/cart/items")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("productId", productId.toString())
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        Integer quantity = jdbcTemplate.queryForObject("""
                select ci.quantity
                from cart_items ci
                join carts c on c.id = ci.cart_id
                join users u on u.id = c.user_id
                where u.email = ? and ci.product_id = ?
                """, Integer.class, email, productId);
        assertEquals(1, quantity);
    }

    @Test
    void userUpdateIsClampedToCurrentDatabaseStock() throws Exception {
        String email = "cart-update-" + UUID.randomUUID() + "@example.com";
        insertUser(email);
        Long productId = insertSaleableProductFixture();

        mockMvc.perform(post("/cart/items")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("productId", productId.toString())
                        .param("quantity", "1"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/cart/items/{productId}/quantity", productId)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("quantity", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        Integer quantity = jdbcTemplate.queryForObject("""
                select ci.quantity
                from cart_items ci
                join carts c on c.id = ci.cart_id
                join users u on u.id = c.user_id
                where u.email = ? and ci.product_id = ?
                """, Integer.class, email, productId);
        assertEquals(5, quantity);
    }

    private Long insertSaleableProductFixture() {
        String suffix = UUID.randomUUID().toString();
        String ownerEmail = "cart-owner-" + suffix + "@example.com";
        insertUser(ownerEmail);
        Long ownerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, ownerEmail);

        String shopName = "Cart Integration Shop " + suffix;
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   submitted_at, approved_at, created_at, updated_at)
                values (?, ?, '0900000000', 'Test address', 'APPROVED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName);
        Long shopId = jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);

        String categoryName = "Cart Integration Category " + suffix;
        jdbcTemplate.update("""
                insert into categories (name, description, active, created_at, updated_at)
                values (?, 'Test category', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, categoryName);
        Long categoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = ?", Long.class, categoryName);

        String productName = "Cart Integration Product " + suffix;
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
                """, "Cart Integration User", email, "not-a-real-password-hash");
    }
}
