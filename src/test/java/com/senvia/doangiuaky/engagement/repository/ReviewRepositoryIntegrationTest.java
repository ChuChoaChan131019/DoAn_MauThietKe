package com.senvia.doangiuaky.engagement.repository;

import com.senvia.doangiuaky.engagement.entity.Review;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@Transactional
class ReviewRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    void persistsReviewAgainstVerifiedOrderItem() {
        insertFixture();

        Review saved = reviewRepository.saveAndFlush(
                Review.create(900001L, 900001L, 900001L, 5, "Sản phẩm đúng mô tả."));

        assertNotNull(saved.getId());
        assertEquals(900001L, saved.getUserId());
        assertEquals(900001L, saved.getProductId());
        assertEquals(900001L, saved.getOrderItemId());
        assertEquals(5, saved.getRating());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void retrievesProductReviewsNewestFirstWithStableIdOrdering() {
        insertFixture();
        insertOrderItem(900002L);

        Review first = reviewRepository.saveAndFlush(
                Review.create(900001L, 900001L, 900001L, 5, "Đánh giá trước."));
        Review second = reviewRepository.saveAndFlush(
                Review.create(900001L, 900001L, 900002L, 4, "Đánh giá sau."));

        List<Long> reviewIds = reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(900001L)
                .stream()
                .map(Review::getId)
                .toList();

        assertEquals(List.of(second.getId(), first.getId()), reviewIds);
    }

    private void insertFixture() {
        jdbcTemplate.update(
                "INSERT INTO users (id, full_name, email, password_hash) VALUES (?, ?, ?, ?)",
                900001L, "Review Buyer", "review-buyer-900001@example.test", "not-a-real-password");
        jdbcTemplate.update(
                "INSERT INTO shops (id, owner_id, shop_name, phone, address) VALUES (?, ?, ?, ?, ?)",
                900001L, 900001L, "Review Shop", "0000000000", "Test Address");
        jdbcTemplate.update(
                "INSERT INTO categories (id, name) VALUES (?, ?)",
                900001L, "Review Category");
        jdbcTemplate.update(
                "INSERT INTO products (id, shop_id, category_id, name, description, price, stock_quantity) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                900001L, 900001L, 900001L, "Review Product", "Test product description.",
                new BigDecimal("100.00"), 1);
        jdbcTemplate.update(
                "INSERT INTO orders (id, order_code, buyer_id, shop_id, receiver_name, receiver_phone, "
                        + "shipping_address, total_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                900001L, "ORD-REV-900001", 900001L, 900001L, "Review Buyer", "0000000000",
                "Test Address", new BigDecimal("100.00"));
        jdbcTemplate.update(
                "INSERT INTO order_items (id, order_id, product_id, product_name_snapshot, unit_price, "
                        + "quantity, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)",
                900001L, 900001L, 900001L, "Review Product", new BigDecimal("100.00"), 1,
                new BigDecimal("100.00"));
    }

    private void insertOrderItem(long id) {
        jdbcTemplate.update(
                "INSERT INTO order_items (id, order_id, product_id, product_name_snapshot, unit_price, "
                        + "quantity, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, 900001L, 900001L, "Review Product", new BigDecimal("100.00"), 1,
                new BigDecimal("100.00"));
    }
}
