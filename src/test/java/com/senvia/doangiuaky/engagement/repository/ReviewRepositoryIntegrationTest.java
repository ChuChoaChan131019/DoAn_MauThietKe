package com.senvia.doangiuaky.engagement.repository;

import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.entity.Review;
import com.senvia.doangiuaky.engagement.service.ReviewService;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.api.ReviewOrderApi;
import com.senvia.doangiuaky.ordering.api.ReviewableOrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;

@SpringBootTest
@Transactional
class ReviewRepositoryIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ReviewRepository reviewRepository;

    @Test
    void persistsReviewAgainstVerifiedOrderItem() {
        insertFixture(900001L);

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentSubmissionsReturnConflictForDuplicateOrderItem() throws Exception {
        long fixtureId = 900002L;
        deleteFixture(fixtureId);
        insertFixture(fixtureId);
        String orderCode = "ORD-REV-" + fixtureId;
        ReviewOrderApi reviewOrderApi = mock(ReviewOrderApi.class);
        when(reviewOrderApi.requireReviewableItem(fixtureId, fixtureId))
                .thenReturn(new ReviewableOrderItem(
                        fixtureId, fixtureId, orderCode, fixtureId, fixtureId, fixtureId));
        CyclicBarrier bothPassedDuplicateCheck = new CyclicBarrier(2);
        ReviewRepository synchronizedCheckRepository = mock(ReviewRepository.class);
        doAnswer(invocation -> {
            bothPassedDuplicateCheck.await(10, TimeUnit.SECONDS);
            return false;
        }).when(synchronizedCheckRepository).existsByOrderItemId(fixtureId);
        when(synchronizedCheckRepository.saveAndFlush(any(Review.class)))
                .thenAnswer(invocation -> reviewRepository.saveAndFlush(invocation.getArgument(0)));
        ReviewService reviewService = new ReviewService(
                reviewOrderApi, synchronizedCheckRepository, mock(MerchantApi.class));
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var submission = (java.util.concurrent.Callable<Integer>) () -> {
                try {
                    transactionTemplate.execute(transactionStatus -> reviewService.createReview(form(fixtureId)));
                    return CREATED.value();
                } catch (ResponseStatusException exception) {
                    return exception.getStatusCode().value();
                }
            };
            List<Future<Integer>> results = executor.invokeAll(List.of(submission, submission));

            List<Integer> statuses = List.of(
                    results.get(0).get(10, TimeUnit.SECONDS),
                    results.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, statuses.stream().filter(status -> status == CREATED.value()).count());
            assertEquals(1, statuses.stream().filter(status -> status == CONFLICT.value()).count());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM reviews WHERE order_item_id = ?", Integer.class, fixtureId));
        } finally {
            executor.shutdownNow();
            deleteFixture(fixtureId);
        }
    }

    @Test
    void retrievesProductReviewsNewestFirstWithStableIdOrdering() {
        insertFixture(900001L);
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

    private void insertFixture(long id) {
        jdbcTemplate.update(
                "INSERT INTO users (id, full_name, email, password_hash) VALUES (?, ?, ?, ?)",
                id, "Review Buyer", "review-buyer-" + id + "@example.test", "not-a-real-password");
        jdbcTemplate.update(
                "INSERT INTO shops (id, owner_id, shop_name, phone, address) VALUES (?, ?, ?, ?, ?)",
                id, id, "Review Shop", "0000000000", "Test Address");
        jdbcTemplate.update(
                "INSERT INTO categories (id, name) VALUES (?, ?)",
                id, "Review Category");
        jdbcTemplate.update(
                "INSERT INTO products (id, shop_id, category_id, name, description, price, stock_quantity) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, id, id, "Review Product", "Test product description.",
                new BigDecimal("100.00"), 1);
        jdbcTemplate.update(
                "INSERT INTO orders (id, order_code, buyer_id, shop_id, receiver_name, receiver_phone, "
                        + "shipping_address, total_amount) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, "ORD-REV-" + id, id, id, "Review Buyer", "0000000000",
                "Test Address", new BigDecimal("100.00"));
        jdbcTemplate.update(
                "INSERT INTO order_items (id, order_id, product_id, product_name_snapshot, unit_price, "
                        + "quantity, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, id, id, "Review Product", new BigDecimal("100.00"), 1,
                new BigDecimal("100.00"));
    }

    private void deleteFixture(long id) {
        jdbcTemplate.update("DELETE FROM reviews WHERE order_item_id = ?", id);
        jdbcTemplate.update("DELETE FROM order_items WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM orders WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM products WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM categories WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM shops WHERE id = ?", id);
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
    }

    private static ReviewForm form(long id) {
        ReviewForm form = new ReviewForm();
        form.setOrderItemId(id);
        form.setProductId(id);
        form.setRating(5);
        form.setComment("Concurrent submission.");
        return form;
    }

    private void insertOrderItem(long id) {
        jdbcTemplate.update(
                "INSERT INTO order_items (id, order_id, product_id, product_name_snapshot, unit_price, "
                        + "quantity, subtotal) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id, 900001L, 900001L, "Review Product", new BigDecimal("100.00"), 1,
                new BigDecimal("100.00"));
    }
}
