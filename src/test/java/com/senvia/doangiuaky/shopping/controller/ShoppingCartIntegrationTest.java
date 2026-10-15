package com.senvia.doangiuaky.shopping.controller;

import com.senvia.doangiuaky.shopping.dto.CartView;
import com.senvia.doangiuaky.ordering.dto.CheckoutSelectionView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

        Long cartItemId = jdbcTemplate.queryForObject("""
                select ci.id
                from cart_items ci
                join carts c on c.id = ci.cart_id
                join users u on u.id = c.user_id
                where u.email = ? and ci.product_id = ?
                """, Long.class, email, productId);

        mockMvc.perform(post("/cart/items/{cartItemId}/quantity", cartItemId)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("quantity", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("cartWarning",
                        "Số lượng đã được điều chỉnh còn 5 theo tồn kho hiện tại (5)."));

        Integer quantity = jdbcTemplate.queryForObject("""
                select ci.quantity
                from cart_items ci
                join carts c on c.id = ci.cart_id
                join users u on u.id = c.user_id
                where u.email = ? and ci.product_id = ?
                """, Integer.class, email, productId);
        assertEquals(5, quantity);
    }

    @Test
    void invalidUpdateQuantitiesDoNotChangeDatabase() throws Exception {
        String email = "cart-invalid-update-" + UUID.randomUUID() + "@example.com";
        insertUser(email);
        Long productId = insertSaleableProductFixture();

        mockMvc.perform(post("/cart/items")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("productId", productId.toString())
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection());
        Long cartItemId = cartItemId(email, productId);

        mockMvc.perform(post("/cart/items/{cartItemId}/quantity", cartItemId)
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("cartError", "Số lượng là bắt buộc."));
        mockMvc.perform(post("/cart/items/{cartItemId}/quantity", cartItemId)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("quantity", "0"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("cartError", "Số lượng phải lớn hơn 0."));
        mockMvc.perform(post("/cart/items/{cartItemId}/quantity", cartItemId)
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("quantity", "-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("cartError", "Số lượng phải lớn hơn 0."));

        assertEquals(2, quantityForUser(email, productId));
    }

    @Test
    void removeDeletesOnlyRequestedCartItem() throws Exception {
        String email = "cart-remove-" + UUID.randomUUID() + "@example.com";
        insertUser(email);
        Long firstProductId = insertSaleableProductFixture();
        Long secondProductId = insertSaleableProductFixture();

        mockMvc.perform(post("/cart/items")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("productId", firstProductId.toString())
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/cart/items")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("productId", secondProductId.toString())
                        .param("quantity", "3"))
                .andExpect(status().is3xxRedirection());
        Long firstItemId = cartItemId(email, firstProductId);

        mockMvc.perform(post("/cart/items/{cartItemId}/remove", firstItemId)
                        .with(user(email).roles("USER"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"))
                .andExpect(flash().attribute("cartSuccess", "Đã xóa sản phẩm khỏi giỏ hàng."));

        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from cart_items where id = ?", Integer.class, firstItemId));
        assertEquals(3, quantityForUser(email, secondProductId));
    }

    @Test
    void cartPageGroupsByShopAndReloadsCurrentPricesWithoutChangingCartItems() throws Exception {
        String suffix = UUID.randomUUID().toString();
        String buyerEmail = "cart-view-buyer-" + suffix + "@example.com";
        insertUser(buyerEmail);
        Long buyerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, buyerEmail);

        Long categoryId = insertActiveCategory("Cart View Category " + suffix);
        Long firstShopId = insertApprovedShop("Cart View Shop A " + suffix, suffix + "-a");
        Long secondShopId = insertApprovedShop("Cart View Shop B " + suffix, suffix + "-b");
        String firstProductName = "Cart View Product A " + suffix;
        String secondProductName = "Cart View Product B " + suffix;
        String thirdProductName = "Cart View Product C " + suffix;
        Long firstProductId = insertProduct(
                firstShopId, categoryId, firstProductName, new BigDecimal("100000.00"));
        Long secondProductId = insertProduct(
                firstShopId, categoryId, secondProductName, new BigDecimal("25000.00"));
        Long thirdProductId = insertProduct(
                secondShopId, categoryId, thirdProductName, new BigDecimal("80000.00"));
        Long cartId = insertCart(buyerId);
        insertCartItem(cartId, firstProductId, 1);
        insertCartItem(cartId, secondProductId, 2);
        insertCartItem(cartId, thirdProductId, 3);

        var firstResponse = mockMvc.perform(get("/cart").with(user(buyerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cart View Shop A " + suffix)))
                .andExpect(content().string(containsString("Cart View Shop B " + suffix)))
                .andExpect(content().string(containsString(firstProductName)))
                .andExpect(content().string(containsString(secondProductName)))
                .andExpect(content().string(containsString(thirdProductName)))
                .andExpect(content().string(containsString("150,000₫")))
                .andExpect(content().string(containsString("240,000₫")))
                .andExpect(content().string(containsString("390,000₫")))
                .andExpect(content().string(containsString("form=\"checkout-selection\"")))
                .andExpect(content().string(containsString("name=\"itemIds\"")))
                .andReturn();
        CartView firstCart = (CartView) firstResponse.getModelAndView().getModel().get("cart");
        assertEquals(2, firstCart.shops().size());
        assertEquals(2, firstCart.shops().get(0).items().size());
        assertEquals(new BigDecimal("150000.00"), firstCart.shops().get(0).subtotal());
        assertEquals(new BigDecimal("240000.00"), firstCart.shops().get(1).subtotal());
        assertEquals(new BigDecimal("390000.00"), firstCart.subtotal());

        jdbcTemplate.update(
                "update products set price = ?, updated_at = CURRENT_TIMESTAMP where id = ?",
                new BigDecimal("110000.00"), firstProductId);

        var secondResponse = mockMvc.perform(get("/cart").with(user(buyerEmail).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("110,000₫")))
                .andExpect(content().string(containsString("160,000₫")))
                .andExpect(content().string(containsString("400,000₫")))
                .andReturn();
        CartView secondCart = (CartView) secondResponse.getModelAndView().getModel().get("cart");
        assertEquals(new BigDecimal("110000.00"),
                secondCart.shops().get(0).items().get(0).currentPrice());
        assertEquals(new BigDecimal("160000.00"), secondCart.shops().get(0).subtotal());
        assertEquals(new BigDecimal("400000.00"), secondCart.subtotal());

        assertEquals(3, jdbcTemplate.queryForObject(
                "select count(*) from cart_items where cart_id = ?", Integer.class, cartId));
        assertEquals(1, quantityFor(cartId, firstProductId));
        assertEquals(2, quantityFor(cartId, secondProductId));
        assertEquals(3, quantityFor(cartId, thirdProductId));
        assertEquals(0, jdbcTemplate.queryForObject("""
                select count(*)
                from information_schema.columns
                where lower(table_name) = 'cart_items'
                  and lower(column_name) in ('price', 'current_price', 'unit_price')
                """, Integer.class));
    }

    @Test
    void cartPageWarnsWhenStockBecomesUnavailableWithoutChangingCart() throws Exception {
        String email = "cart-read-" + UUID.randomUUID() + "@example.com";
        insertUser(email);
        Long productId = insertSaleableProductFixture();

        mockMvc.perform(post("/cart/items")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("productId", productId.toString())
                        .param("quantity", "2"))
                .andExpect(status().is3xxRedirection());

        jdbcTemplate.update("update products set stock_quantity = 0 where id = ?", productId);

        mockMvc.perform(get("/cart").with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sản phẩm đã hết hàng.")))
                .andExpect(content().string(containsString("disabled")))
                .andExpect(content().string(not(containsString("href=\"/checkout\""))))
                .andExpect(content().string(containsString("name=\"itemIds\"")));

        assertEquals(2, jdbcTemplate.queryForObject("""
                select ci.quantity
                from cart_items ci
                join carts c on c.id = ci.cart_id
                join users u on u.id = c.user_id
                where u.email = ? and ci.product_id = ?
                """, Integer.class, email, productId));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select stock_quantity from products where id = ?", Integer.class, productId));
    }

    @Test
    void mixedCartCanPreviewOnlySelectedValidItemAndRejectInvalidOrForeignSelection() throws Exception {
        String email = "cart-select-" + UUID.randomUUID() + "@example.com";
        String anotherEmail = "cart-other-" + UUID.randomUUID() + "@example.com";
        insertUser(email);
        insertUser(anotherEmail);
        Long validProductId = insertSaleableProductFixture();
        Long invalidProductId = insertSaleableProductFixture();
        for (Long productId : java.util.List.of(validProductId, invalidProductId)) {
            mockMvc.perform(post("/cart/items")
                            .with(user(email).roles("USER"))
                            .with(csrf())
                            .param("productId", productId.toString())
                            .param("quantity", "1"))
                    .andExpect(status().is3xxRedirection());
        }
        Long validItemId = cartItemId(email, validProductId);
        Long invalidItemId = cartItemId(email, invalidProductId);
        jdbcTemplate.update("update products set stock_quantity = 0 where id = ?", invalidProductId);

        var cartResponse = mockMvc.perform(get("/cart").with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andReturn();
        CartView cart = (CartView) cartResponse.getModelAndView().getModel().get("cart");
        assertEquals(1, cart.shops().stream().flatMap(shop -> shop.items().stream())
                .filter(item -> item.available()).count());
        assertEquals(1, cart.shops().stream().flatMap(shop -> shop.items().stream())
                .filter(item -> !item.available()).count());
        assertTrue(cart.canCheckout());

        var checkoutResponse = mockMvc.perform(get("/checkout")
                        .with(user(email).roles("USER"))
                        .param("itemIds", validItemId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("100,000₫")))
                .andReturn();
        CheckoutSelectionView selection = (CheckoutSelectionView) checkoutResponse
                .getModelAndView().getModel().get("checkoutSelection");
        assertEquals(java.util.List.of(validItemId), selection.shops().stream()
                .flatMap(shop -> shop.items().stream())
                .map(CheckoutSelectionView.Item::cartItemId).toList());

        mockMvc.perform(get("/checkout").with(user(email).roles("USER"))
                        .param("itemIds", validItemId.toString(), invalidItemId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sản phẩm được chọn không còn hợp lệ")));
        mockMvc.perform(get("/checkout").with(user(anotherEmail).roles("USER"))
                        .param("itemIds", validItemId.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sản phẩm được chọn không còn trong giỏ hàng")));
        mockMvc.perform(get("/checkout").with(user(email).roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Vui lòng chọn ít nhất một sản phẩm")));
        mockMvc.perform(post("/checkout")
                        .with(user(email).roles("USER"))
                        .with(csrf())
                        .param("itemIds", invalidItemId.toString())
                        .param("receiverName", "Buyer")
                        .param("receiverPhone", "0900000000")
                        .param("shippingAddress", "Test address")
                        .param("paymentMethod", "COD"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sản phẩm được chọn không còn hợp lệ")));
        assertEquals(1, quantityForUser(email, validProductId));
        assertEquals(1, quantityForUser(email, invalidProductId));
    }

    private Long insertSaleableProductFixture() {
        String suffix = UUID.randomUUID().toString();
        Long shopId = insertApprovedShop("Cart Integration Shop " + suffix, suffix);
        Long categoryId = insertActiveCategory("Cart Integration Category " + suffix);
        return insertProduct(shopId, categoryId, "Cart Integration Product " + suffix,
                new BigDecimal("100000.00"));
    }

    private Long insertApprovedShop(String shopName, String suffix) {
        String ownerEmail = "cart-owner-" + suffix + "@example.com";
        insertUser(ownerEmail);
        Long ownerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, ownerEmail);
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   submitted_at, approved_at, created_at, updated_at)
                values (?, ?, '0900000000', 'Test address', 'APPROVED',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName);
        return jdbcTemplate.queryForObject(
                "select id from shops where owner_id = ?", Long.class, ownerId);
    }

    private Long insertActiveCategory(String categoryName) {
        jdbcTemplate.update("""
                insert into categories (name, description, active, created_at, updated_at)
                values (?, 'Test category', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, categoryName);
        return jdbcTemplate.queryForObject(
                "select id from categories where name = ?", Long.class, categoryName);
    }

    private Long insertProduct(
            Long shopId,
            Long categoryId,
            String productName,
            BigDecimal price) {
        jdbcTemplate.update("""
                insert into products (shop_id, category_id, name, description, price,
                                      stock_quantity, status, created_at, updated_at)
                values (?, ?, ?, 'Test product', ?, 5, 'ACTIVE',
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, shopId, categoryId, productName, price);
        return jdbcTemplate.queryForObject(
                "select id from products where shop_id = ? and name = ?",
                Long.class, shopId, productName);
    }

    private Long cartItemId(String email, Long productId) {
        return jdbcTemplate.queryForObject("""
                select ci.id
                from cart_items ci
                join carts c on c.id = ci.cart_id
                join users u on u.id = c.user_id
                where u.email = ? and ci.product_id = ?
                """, Long.class, email, productId);
    }

    private int quantityForUser(String email, Long productId) {
        return jdbcTemplate.queryForObject("""
                select ci.quantity
                from cart_items ci
                join carts c on c.id = ci.cart_id
                join users u on u.id = c.user_id
                where u.email = ? and ci.product_id = ?
                """, Integer.class, email, productId);
    }

    private Long insertCart(Long userId) {
        jdbcTemplate.update("""
                insert into carts (user_id, created_at, updated_at)
                values (?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, userId);
        return jdbcTemplate.queryForObject(
                "select id from carts where user_id = ?", Long.class, userId);
    }

    private void insertCartItem(Long cartId, Long productId, int quantity) {
        jdbcTemplate.update("""
                insert into cart_items (cart_id, product_id, quantity, created_at, updated_at)
                values (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, cartId, productId, quantity);
    }

    private int quantityFor(Long cartId, Long productId) {
        return jdbcTemplate.queryForObject(
                "select quantity from cart_items where cart_id = ? and product_id = ?",
                Integer.class, cartId, productId);
    }

    private void insertUser(String email) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status,
                                   created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Cart Integration User", email, "not-a-real-password-hash");
    }
}
