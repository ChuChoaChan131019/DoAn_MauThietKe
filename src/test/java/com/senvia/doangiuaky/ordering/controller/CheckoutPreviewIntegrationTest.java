package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.ordering.service.PreparedCheckout;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutPreviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void selectedItemsFromTwoShopsPreviewWithoutWritingOrIncludingUnavailableUnselectedItem() throws Exception {
        String email = "preview-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values ('Preview Test User', ?, 'test-only-hash', 'USER', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, email);
        Long buyerId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
        Long toteId = productId("Canvas Tote Bag");
        Long lampId = productId("Brass Table Lamp");
        Long unavailableId = productId("Linen Shirt");

        jdbcTemplate.update("insert into carts (user_id) values (?)", buyerId);
        Long cartId = jdbcTemplate.queryForObject("select id from carts where user_id = ?", Long.class, buyerId);
        Long toteItemId = addItem(cartId, toteId, 2);
        Long lampItemId = addItem(cartId, lampId, 1);
        addItem(cartId, unavailableId, 1);

        Integer ordersBefore = count("orders");
        Integer paymentsBefore = count("payments");
        Integer orderItemsBefore = count("order_items");
        Integer toteStock = stock(toteId);
        Integer lampStock = stock(lampId);

        mockMvc.perform(get("/checkout")
                        .with(user(email).roles("USER"))
                        .param("itemIds", toteItemId.toString(), lampItemId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Linen Shirt"))));

        var result = mockMvc.perform(post("/checkout")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("itemIds", toteItemId.toString(), lampItemId.toString())
                        .param("receiverName", "  Preview Buyer  ")
                        .param("receiverPhone", "0901234567")
                        .param("shippingAddress", "  123 Test Street  ")
                        .param("paymentMethod", "COD"))
                .andExpect(status().isOk())
                .andExpect(view().name("ordering/checkout/index"))
                .andExpect(model().attributeExists("checkoutPreview"))
                .andExpect(content().string(containsString("NOLA Store")))
                .andExpect(content().string(containsString("KANSO Store")))
                .andExpect(content().string(containsString("3,670,000₫")))
                .andExpect(content().string(not(containsString("Linen Shirt"))))
                .andReturn();

        PreparedCheckout preview = (PreparedCheckout) result.getModelAndView().getModel().get("checkoutPreview");
        assertNotNull(preview);
        assertEquals(buyerId, preview.buyerId());
        assertEquals("Preview Buyer", preview.delivery().receiverName());
        assertEquals("123 Test Street", preview.delivery().shippingAddress());
        assertEquals(2, preview.shopOrders().size());
        assertEquals(0, preview.grandTotal().compareTo(new BigDecimal("3670000.00")));
        assertEquals(2, preview.purchasedCartItems().size());
        assertEquals(List.of(toteItemId, lampItemId), preview.purchasedCartItems().stream()
                .map(item -> item.cartItemId()).toList());
        assertEquals(ordersBefore, count("orders"));
        assertEquals(paymentsBefore, count("payments"));
        assertEquals(orderItemsBefore, count("order_items"));
        assertEquals(3, jdbcTemplate.queryForObject("select count(*) from cart_items where cart_id = ?", Integer.class, cartId));
        assertEquals(toteStock, stock(toteId));
        assertEquals(lampStock, stock(lampId));
    }

    private Long productId(String name) {
        return jdbcTemplate.queryForObject("select id from products where name = ?", Long.class, name);
    }

    private Long addItem(Long cartId, Long productId, int quantity) {
        jdbcTemplate.update("insert into cart_items (cart_id, product_id, quantity) values (?, ?, ?)",
                cartId, productId, quantity);
        return jdbcTemplate.queryForObject("select id from cart_items where cart_id = ? and product_id = ?",
                Long.class, cartId, productId);
    }

    private Integer stock(Long productId) {
        return jdbcTemplate.queryForObject("select stock_quantity from products where id = ?", Integer.class, productId);
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }
}
