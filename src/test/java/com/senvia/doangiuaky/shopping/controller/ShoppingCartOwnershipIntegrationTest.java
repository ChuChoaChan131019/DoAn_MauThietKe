package com.senvia.doangiuaky.shopping.controller;

import com.senvia.doangiuaky.shopping.entity.Cart;
import com.senvia.doangiuaky.shopping.entity.CartItem;
import com.senvia.doangiuaky.shopping.repository.CartItemRepository;
import com.senvia.doangiuaky.shopping.repository.CartRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ShoppingCartOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private CartRepository cartRepository;
    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void authenticatedBuyerCannotReadUpdateOrRemoveAnotherBuyersCartItem() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(get("/cart")
                        .with(user(fixture.firstEmail()).roles("USER"))
                        .param("buyerId", fixture.secondBuyerId().toString())
                        .header("X-Buyer-Id", fixture.secondBuyerId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(fixture.firstProductName())))
                .andExpect(content().string(not(containsString(fixture.secondProductName()))));

        mockMvc.perform(post("/cart/items/{id}/quantity", fixture.secondItemId())
                        .with(user(fixture.firstEmail()).roles("USER"))
                        .with(csrf())
                        .param("buyerId", fixture.secondBuyerId().toString())
                        .param("quantity", "4"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/cart/items/{id}/remove", fixture.secondItemId())
                        .with(user(fixture.firstEmail()).roles("USER"))
                        .with(csrf())
                        .param("buyerId", fixture.secondBuyerId().toString()))
                .andExpect(status().isNotFound());

        CartItem untouched = cartItemRepository.findById(fixture.secondItemId()).orElseThrow();
        assertEquals(2, untouched.getQuantity());
    }

    @Test
    void authenticatedBuyerCanUpdateAndRemoveOwnItem() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/cart/items/{id}/quantity", fixture.firstItemId())
                        .with(user(fixture.firstEmail()).roles("USER"))
                        .with(csrf())
                        .param("buyerId", fixture.secondBuyerId().toString())
                        .param("quantity", "4"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
        assertEquals(4, cartItemRepository.findById(fixture.firstItemId()).orElseThrow().getQuantity());

        mockMvc.perform(post("/cart/items/{id}/remove", fixture.firstItemId())
                        .with(user(fixture.firstEmail()).roles("USER"))
                        .with(csrf())
                        .param("buyerId", fixture.secondBuyerId().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));
        assertTrue(cartItemRepository.findById(fixture.firstItemId()).isEmpty());
        assertTrue(cartItemRepository.findById(fixture.secondItemId()).isPresent());
    }

    @Test
    void missingItemReturnsNotFoundWithoutChangingOwnedItems() throws Exception {
        Fixture fixture = createFixture();
        long missingItemId = Long.MAX_VALUE;

        mockMvc.perform(post("/cart/items/{id}/quantity", missingItemId)
                        .with(user(fixture.firstEmail()).roles("USER"))
                        .with(csrf())
                        .param("quantity", "4"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/cart/items/{id}/remove", missingItemId)
                        .with(user(fixture.firstEmail()).roles("USER"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        CartItem ownedItem = cartItemRepository.findById(fixture.firstItemId()).orElseThrow();
        assertEquals(1, ownedItem.getQuantity());
        assertFalse(cartItemRepository.findById(fixture.firstItemId()).isEmpty());
    }

    @Test
    void unauthenticatedAndInactivePrincipalsCannotAccessCart() throws Exception {
        mockMvc.perform(get("/cart"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        String lockedEmail = "locked-cart-" + UUID.randomUUID() + "@example.com";
        insertUser(lockedEmail, "LOCKED");
        mockMvc.perform(get("/cart").with(user(lockedEmail).roles("USER")))
                .andExpect(status().isForbidden());
    }

    private Fixture createFixture() {
        String suffix = UUID.randomUUID().toString();
        String firstEmail = "cart-a-" + suffix + "@example.com";
        String secondEmail = "cart-b-" + suffix + "@example.com";
        String ownerEmail = "cart-owner-" + suffix + "@example.com";
        Long firstBuyerId = insertUser(firstEmail, "ACTIVE");
        Long secondBuyerId = insertUser(secondEmail, "ACTIVE");
        Long ownerId = insertUser(ownerEmail, "ACTIVE");

        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   submitted_at, approved_at, created_at, updated_at)
                values (?, ?, '0900000000', 'Test address', 'APPROVED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, "ownership-shop-" + suffix);
        Long shopId = jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);
        jdbcTemplate.update("""
                insert into categories (name, active, created_at, updated_at)
                values (?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "ownership-category-" + suffix);
        Long categoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = ?", Long.class, "ownership-category-" + suffix);
        String firstProductName = "Buyer A Product " + suffix;
        String secondProductName = "Buyer B Product " + suffix;
        Long firstProductId = insertProduct(shopId, categoryId, firstProductName);
        Long secondProductId = insertProduct(shopId, categoryId, secondProductName);

        Cart firstCart = cartRepository.saveAndFlush(new Cart(firstBuyerId));
        Cart secondCart = cartRepository.saveAndFlush(new Cart(secondBuyerId));
        CartItem firstItem = cartItemRepository.saveAndFlush(new CartItem(firstCart.getId(), firstProductId, 1));
        CartItem secondItem = cartItemRepository.saveAndFlush(new CartItem(secondCart.getId(), secondProductId, 2));
        return new Fixture(firstEmail, secondBuyerId, firstItem.getId(), secondItem.getId(),
                firstProductName, secondProductName);
    }

    private Long insertUser(String email, String status) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status,
                                   created_at, updated_at)
                values ('Cart Ownership User', ?, 'not-a-real-password-hash', 'USER', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, email);
        Long userId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
        if ("LOCKED".equals(status)) {
            Long adminId = jdbcTemplate.queryForObject(
                    "select min(id) from users where id <> ?", Long.class, userId);
            jdbcTemplate.update("""
                    update users
                    set account_status = 'LOCKED', lock_reason = 'Integration test lock',
                        locked_by = ?, locked_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    where id = ?
                    """, adminId, userId);
        }
        return userId;
    }

    private Long insertProduct(Long shopId, Long categoryId, String name) {
        jdbcTemplate.update("""
                insert into products (shop_id, category_id, name, description, price,
                                      stock_quantity, status, created_at, updated_at)
                values (?, ?, ?, 'Ownership test product', 100.00, 10, 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, shopId, categoryId, name);
        return jdbcTemplate.queryForObject("select id from products where name = ?", Long.class, name);
    }

    private record Fixture(String firstEmail, Long secondBuyerId, Long firstItemId, Long secondItemId,
                           String firstProductName, String secondProductName) {
    }
}
