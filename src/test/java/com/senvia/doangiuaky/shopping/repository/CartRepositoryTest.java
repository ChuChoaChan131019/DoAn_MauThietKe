package com.senvia.doangiuaky.shopping.repository;

import com.senvia.doangiuaky.shopping.entity.Cart;
import com.senvia.doangiuaky.shopping.entity.CartItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void oneUserCannotHaveTwoCarts() {
        Long userId = insertUser("cart-one@example.com");
        cartRepository.saveAndFlush(new Cart(userId));

        assertThrows(DataIntegrityViolationException.class,
                () -> cartRepository.saveAndFlush(new Cart(userId)));
    }

    @Test
    void oneCartCannotHaveDuplicateProductRowsOrNonPositiveQuantity() {
        Long userId = insertUser("cart-two@example.com");
        Long shopId = insertShop(userId, "cart-two-shop");
        Long categoryId = insertCategory("cart-two-category");
        Long productId = insertProduct(shopId, categoryId, "cart-two-product");
        Long anotherProductId = insertProduct(shopId, categoryId, "cart-two-another-product");
        Cart cart = cartRepository.saveAndFlush(new Cart(userId));
        cartItemRepository.saveAndFlush(new CartItem(cart.getId(), productId, 1));

        assertThrows(DataIntegrityViolationException.class,
                () -> cartItemRepository.saveAndFlush(new CartItem(cart.getId(), productId, 1)));
        assertThrows(DataIntegrityViolationException.class,
                () -> cartItemRepository.saveAndFlush(new CartItem(cart.getId(), anotherProductId, 0)));
        assertThrows(DataIntegrityViolationException.class,
                () -> cartItemRepository.saveAndFlush(new CartItem(cart.getId(), 999999L, 1)));
    }

    private Long insertUser(String email) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Cart User", email, "not-a-real-password-hash");
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long insertShop(Long ownerId, String name) {
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status, created_at, updated_at)
                values (?, ?, ?, ?, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, name, "0900000000", "Test address");
        return jdbcTemplate.queryForObject("select id from shops where shop_name = ?", Long.class, name);
    }

    private Long insertCategory(String name) {
        jdbcTemplate.update("""
                insert into categories (name, active, created_at, updated_at)
                values (?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, name);
        return jdbcTemplate.queryForObject("select id from categories where name = ?", Long.class, name);
    }

    private Long insertProduct(Long shopId, Long categoryId, String name) {
        jdbcTemplate.update("""
                insert into products (shop_id, category_id, name, description, price, stock_quantity, status,
                                      created_at, updated_at)
                values (?, ?, ?, 'Test product', 100.00, 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, shopId, categoryId, name);
        return jdbcTemplate.queryForObject("select id from products where name = ?", Long.class, name);
    }
}
