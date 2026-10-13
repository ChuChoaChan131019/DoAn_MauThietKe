package com.senvia.doangiuaky.merchant.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PublicCatalogRenderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void guestCanRenderCatalogDetailAndPublicShopWithRealData() throws Exception {
        Long productId = productId("Canvas Tote Bag");
        Long shopId = shopId(productId);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Linen Shirt"))));
        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOLA Store")))
                .andExpect(content().string(containsString("/merchant/shop-public?shopId=" + shopId)))
                .andExpect(content().string(containsString("/images/product-placeholder.svg")));
        mockMvc.perform(get("/merchant/shop-public").param("shopId", shopId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOLA Store")))
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Linen Shirt"))));
    }

    @Test
    void userCanRenderCatalogAndDetail() throws Exception {
        Long productId = productId("Canvas Tote Bag");

        mockMvc.perform(get("/products").with(user("buyer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")));
        mockMvc.perform(get("/products/{id}", productId).with(user("buyer").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("NOLA Store")));
    }

    @Test
    void shopFilterUsesShopParameterAndAcceptsLegacyBrandLinks() throws Exception {
        mockMvc.perform(get("/products").param("shop", "NOLA Store"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Brass Table Lamp"))));
        mockMvc.perform(get("/products").param("brand", "NOLA Store"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Canvas Tote Bag")))
                .andExpect(content().string(not(containsString("Brass Table Lamp"))));
    }

    @Test
    void lockedShopIsAbsentFromCatalogAndBothPublicDetailsReturnNotFound() throws Exception {
        Long productId = productId("Canvas Tote Bag");
        Long shopId = shopId(productId);
        Long ownerId = jdbcTemplate.queryForObject(
                "select owner_id from shops where id = ?", Long.class, shopId);
        jdbcTemplate.update("""
                update shops set status = 'LOCKED', lock_reason = 'test',
                       locked_by = ?, locked_at = CURRENT_TIMESTAMP where id = ?
                """, ownerId, shopId);

        mockMvc.perform(get("/products"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Canvas Tote Bag"))));
        mockMvc.perform(get("/products/{id}", productId))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/merchant/shop-public").param("shopId", shopId.toString()))
                .andExpect(status().isNotFound());
    }

    private Long productId(String name) {
        return jdbcTemplate.queryForObject("select id from products where name = ?", Long.class, name);
    }

    private Long shopId(Long productId) {
        return jdbcTemplate.queryForObject(
                "select shop_id from products where id = ?", Long.class, productId);
    }
}
