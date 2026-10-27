package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.entity.Review;
import com.senvia.doangiuaky.engagement.repository.ReviewRepository;
import com.senvia.doangiuaky.ordering.api.ReviewOrderApi;
import com.senvia.doangiuaky.ordering.api.ReviewableOrderItem;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class ReviewService {

    private final ReviewOrderApi reviewOrderApi;
    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewOrderApi reviewOrderApi, ReviewRepository reviewRepository) {
        this.reviewOrderApi = reviewOrderApi;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public ReviewableOrderItem requireReviewableItem(Long orderItemId, Long productId) {
        return reviewOrderApi.requireReviewableItem(orderItemId, productId);
    }

    @Transactional
    public String createReview(ReviewForm form) {
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
