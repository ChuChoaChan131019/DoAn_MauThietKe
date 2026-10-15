package com.senvia.doangiuaky.ordering.controller;

import com.senvia.doangiuaky.ordering.service.PreparedCheckout;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutPreviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void previewsRealCartAcrossTwoShopsWithoutWritingCheckoutData() throws Exception {
        String email = "preview-" + UUID.randomUUID() + "@example.com";
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values ('Preview Test User', ?, 'test-only-hash', 'USER', 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, email);
        Long buyerId = jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);

        Long toteId = jdbcTemplate.queryForObject("select id from products where name = 'Canvas Tote Bag'", Long.class);
        Long lampId = jdbcTemplate.queryForObject("select id from products where name = 'Brass Table Lamp'", Long.class);
        jdbcTemplate.update("insert into carts (user_id) values (?)", buyerId);
        Long cartId = jdbcTemplate.queryForObject("select id from carts where user_id = ?", Long.class, buyerId);
        jdbcTemplate.update("insert into cart_items (cart_id, product_id, quantity) values (?, ?, 2)", cartId, toteId);
        jdbcTemplate.update("insert into cart_items (cart_id, product_id, quantity) values (?, ?, 1)", cartId, lampId);

        Integer ordersBefore = jdbcTemplate.queryForObject("select count(*) from orders", Integer.class);
        Integer paymentsBefore = jdbcTemplate.queryForObject("select count(*) from payments", Integer.class);
        Integer itemsBefore = jdbcTemplate.queryForObject("select count(*) from order_items", Integer.class);
        Integer toteStock = jdbcTemplate.queryForObject("select stock_quantity from products where id = ?", Integer.class, toteId);
        Integer lampStock = jdbcTemplate.queryForObject("select stock_quantity from products where id = ?", Integer.class, lampId);

        var result = mockMvc.perform(post("/checkout")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("receiverName", "  Preview Buyer  ")
                        .param("receiverPhone", "0901234567")
                        .param("shippingAddress", "  123 Test Street  ")
                        .param("paymentMethod", "COD"))
                .andExpect(status().isOk())
                .andExpect(view().name("ordering/checkout/index"))
                .andExpect(model().attributeExists("checkoutPreview"))
                .andExpect(content().string(containsString("NOLA Store")))
                .andExpect(content().string(containsString("KANSO Store")))
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(containsString("Brass Table Lamp")))
                .andExpect(content().string(containsString("3.670.000,00")))
                .andReturn();

        PreparedCheckout preview = (PreparedCheckout) result.getModelAndView().getModel().get("checkoutPreview");
        assertNotNull(preview);
        assertEquals(buyerId, preview.buyerId());
        assertEquals("Preview Buyer", preview.delivery().receiverName());
        assertEquals("123 Test Street", preview.delivery().shippingAddress());
        assertEquals(2, preview.shopOrders().size());
        assertEquals(0, preview.grandTotal().compareTo(new BigDecimal("3670000.00")));
        assertEquals(2, preview.purchasedCartItems().size());

        assertEquals(ordersBefore, jdbcTemplate.queryForObject("select count(*) from orders", Integer.class));
        assertEquals(paymentsBefore, jdbcTemplate.queryForObject("select count(*) from payments", Integer.class));
        assertEquals(itemsBefore, jdbcTemplate.queryForObject("select count(*) from order_items", Integer.class));
        assertEquals(2, jdbcTemplate.queryForObject("select count(*) from cart_items where cart_id = ?", Integer.class, cartId));
        assertEquals(toteStock, jdbcTemplate.queryForObject("select stock_quantity from products where id = ?", Integer.class, toteId));
        assertEquals(lampStock, jdbcTemplate.queryForObject("select stock_quantity from products where id = ?", Integer.class, lampId));
    }
}
