package com.senvia.doangiuaky.shopping.repository;

import com.senvia.doangiuaky.shopping.entity.Favorite;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class FavoriteRepositoryTest {

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void favoriteIsOwnedByUserAndDuplicatePairIsRejected() {
        String suffix = UUID.randomUUID().toString();
        Long firstUserId = insertUser("favorite-first-" + suffix + "@example.com");
        Long secondUserId = insertUser("favorite-second-" + suffix + "@example.com");
        Long productId = insertProductFixture(firstUserId, suffix);

        Favorite saved = favoriteRepository.saveAndFlush(new Favorite(firstUserId, productId));

        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());
        assertEquals(1, favoriteRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(firstUserId).size());
        assertEquals(0, favoriteRepository.findAllByUserIdOrderByCreatedAtDescIdDesc(secondUserId).size());
        assertThrows(DataIntegrityViolationException.class,
                () -> favoriteRepository.saveAndFlush(new Favorite(firstUserId, productId)));
    }

    @Test
    void favoriteForeignKeysRejectMissingUserOrProduct() {
        String suffix = UUID.randomUUID().toString();
        Long userId = insertUser("favorite-fk-" + suffix + "@example.com");

        assertThrows(DataIntegrityViolationException.class,
                () -> favoriteRepository.saveAndFlush(new Favorite(userId, 999999999L)));
    }

    private Long insertUser(String email) {
        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status, created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Favorite User", email, "not-a-real-password-hash");
        return jdbcTemplate.queryForObject("select id from users where email = ?", Long.class, email);
    }

    private Long insertProductFixture(Long ownerId, String suffix) {
        String shopName = "Favorite Repository Shop " + suffix;
        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status, created_at, updated_at)
                values (?, ?, '0900000000', 'Test address', 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName);
        Long shopId = jdbcTemplate.queryForObject(
                "select id from shops where shop_name = ?", Long.class, shopName);

        String categoryName = "Favorite Repository Category " + suffix;
        jdbcTemplate.update("""
                insert into categories (name, active, created_at, updated_at)
                values (?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, categoryName);
        Long categoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = ?", Long.class, categoryName);

        String productName = "Favorite Repository Product " + suffix;
        jdbcTemplate.update("""
                insert into products (shop_id, category_id, name, price, stock_quantity, status,
                                      created_at, updated_at)
                values (?, ?, ?, 100.00, 10, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, shopId, categoryId, productName);
        return jdbcTemplate.queryForObject(
                "select id from products where name = ?", Long.class, productName);
    }
}
