package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.dto.ProductReviewsPage;
import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.entity.Review;
import com.senvia.doangiuaky.engagement.repository.ReviewRepository;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.api.ReviewOrderApi;
import com.senvia.doangiuaky.ordering.api.ReviewableOrderItem;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

@Service
public class ReviewService {

    private final ReviewOrderApi reviewOrderApi;
    private final ReviewRepository reviewRepository;
    private final MerchantApi merchantApi;

    public ReviewService(
            ReviewOrderApi reviewOrderApi,
            ReviewRepository reviewRepository,
            MerchantApi merchantApi) {
        this.reviewOrderApi = reviewOrderApi;
        this.reviewRepository = reviewRepository;
        this.merchantApi = merchantApi;
    }

    @Transactional(readOnly = true)
    public ReviewableOrderItem requireReviewableItem(Long orderItemId, Long productId) {
        return reviewOrderApi.requireReviewableItem(orderItemId, productId);
    }

    @Transactional(readOnly = true)
    public ProductReviewsPage getProductReviews(Long productId) {
        if (productId == null || productId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã sản phẩm không hợp lệ.");
        }

        CartProductView product = merchantApi.findProductForCart(productId)
                .filter(CartProductView::isSaleable)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
        List<Review> reviews = reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(productId);
        BigDecimal averageRating = reviews.isEmpty()
                ? null
                : BigDecimal.valueOf(reviews.stream().mapToLong(Review::getRating).sum())
                        .divide(BigDecimal.valueOf(reviews.size()), 2, RoundingMode.HALF_UP);

        return new ProductReviewsPage(
                product.productId(),
                product.productName(),
                product.shopName(),
                product.imageUrl(),
                averageRating,
                reviews.size(),
                reviews.stream()
                        .map(review -> new ProductReviewsPage.ReviewItem(
                                review.getRating(), review.getComment(), review.getCreatedAt()))
                        .toList());
    }

    @Transactional
    public String createReview(ReviewForm form) {
        if (form.getComment() != null
                && form.getComment().length() > ReviewForm.MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Nhận xét không được vượt quá 1.000 ký tự.");
        }

        ReviewableOrderItem item = reviewOrderApi.requireReviewableItem(
                form.getOrderItemId(), form.getProductId());

        if (form.getRating() == null || form.getRating() < 1 || form.getRating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Số sao phải từ 1 đến 5.");
        }

        if (reviewRepository.existsByOrderItemId(item.orderItemId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Mục hàng này đã được đánh giá.");
        }

        Review review = Review.create(
                item.buyerId(),
                item.productId(),
                item.orderItemId(),
                form.getRating(),
                form.getComment());
        try {
            reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateOrderItem(exception)) {
                throw duplicateReview();
            }
            throw exception;
        }
        return item.orderCode();
    }

    private static boolean isDuplicateOrderItem(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException constraintViolation
                    && constraintViolation.getConstraintName() != null
                    && constraintViolation.getConstraintName()
                    .toLowerCase(Locale.ROOT).contains("uk_reviews_order_item")) {
                return true;
            }
        }
        return false;
    }

    private static ResponseStatusException duplicateReview() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "Mục hàng này đã được đánh giá.");
    }
}
