package com.senvia.doangiuaky.merchant.repository;

import com.senvia.doangiuaky.merchant.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class ProductConstraintIntegrationTest {

    private static final String INSERT_PRODUCT = """
            insert into products (shop_id, category_id, name, price, stock_quantity, status,
                                  created_at, updated_at)
            values (?, ?, ?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsPositivePriceAndZeroStock() {
        Fixture fixture = fixture();

        Product saved = productRepository.saveAndFlush(Product.create(
                fixture.shopId(), fixture.categoryId(), "Boundary product", null,
                new BigDecimal("0.01"), 0));

        BigDecimal storedPrice = jdbcTemplate.queryForObject(
                "select price from products where id = ?", BigDecimal.class, saved.getId());
        Integer storedStock = jdbcTemplate.queryForObject(
                "select stock_quantity from products where id = ?", Integer.class, saved.getId());

        assertEquals(0, storedPrice.compareTo(new BigDecimal("0.01")));
        assertEquals(0, storedStock);
    }

    @Test
    void databaseRejectsZeroPrice() {
        Fixture fixture = fixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> insertProduct(fixture, "zero-price", BigDecimal.ZERO, 1));
    }

    @Test
    void databaseRejectsNegativePrice() {
        Fixture fixture = fixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> insertProduct(fixture, "negative-price", new BigDecimal("-0.01"), 1));
    }

    @Test
    void databaseRejectsNegativeStock() {
        Fixture fixture = fixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> insertProduct(fixture, "negative-stock", new BigDecimal("10.00"), -1));
    }

    @Test
    void databaseRejectsNullPrice() {
        Fixture fixture = fixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> insertProduct(fixture, "null-price", null, 1));
    }

    @Test
    void databaseRejectsNullStock() {
        Fixture fixture = fixture();

        assertThrows(DataIntegrityViolationException.class,
                () -> insertProduct(fixture, "null-stock", new BigDecimal("10.00"), null));
    }

    private void insertProduct(Fixture fixture, String name, BigDecimal price, Integer stockQuantity) {
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(INSERT_PRODUCT);
            statement.setLong(1, fixture.shopId());
            statement.setLong(2, fixture.categoryId());
            statement.setString(3, name);
            if (price == null) {
                statement.setNull(4, Types.NUMERIC);
            } else {
                statement.setBigDecimal(4, price);
            }
            if (stockQuantity == null) {
                statement.setNull(5, Types.INTEGER);
            } else {
                statement.setInt(5, stockQuantity);
            }
            return statement;
        });
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String email = "product-constraint-" + suffix + "@example.test";
        String shopName = "Constraint shop " + suffix;
        String categoryName = "Constraint category " + suffix;

        jdbcTemplate.update("""
                insert into users (full_name, email, password_hash, role, account_status,
                                   created_at, updated_at)
                values (?, ?, ?, 'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, "Product Test User", email, "test-password-hash");
        Long ownerId = jdbcTemplate.queryForObject(
                "select id from users where email = ?", Long.class, email);

        jdbcTemplate.update("""
                insert into shops (owner_id, shop_name, phone, address, status,
                                   created_at, updated_at)
                values (?, ?, ?, ?, 'APPROVED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, shopName, "0900000000", "Test address");
        Long shopId = jdbcTemplate.queryForObject(
                "select id from shops where shop_name = ?", Long.class, shopName);

        jdbcTemplate.update("""
                insert into categories (name, active, created_at, updated_at)
                values (?, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, categoryName);
        Long categoryId = jdbcTemplate.queryForObject(
                "select id from categories where name = ?", Long.class, categoryName);

        return new Fixture(shopId, categoryId);
    }

    private record Fixture(Long shopId, Long categoryId) {
    }
}
