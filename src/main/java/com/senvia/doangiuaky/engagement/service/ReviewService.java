package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.api.ReviewCreatedEvent;
import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.dto.ReviewFormView;
import com.senvia.doangiuaky.engagement.dto.ReviewListItem;
import com.senvia.doangiuaky.engagement.dto.ReviewPageView;
import com.senvia.doangiuaky.engagement.entity.Review;
import com.senvia.doangiuaky.engagement.repository.ReviewRepository;
import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.ordering.api.ReviewOrderApi;
import com.senvia.doangiuaky.ordering.api.ReviewableOrderItem;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

@Service
public class ReviewService {

    private static final int REVIEWS_PER_PAGE = 10;
    private static final String ORDER_ITEM_CONSTRAINT = "uk_reviews_order_item";

    private final CurrentUserApi currentUserApi;
    private final IdentityApi identityApi;
    private final MerchantApi merchantApi;
    private final ReviewOrderApi reviewOrderApi;
    private final ReviewRepository reviews;
    private final ApplicationEventPublisher events;

    public ReviewService(CurrentUserApi currentUserApi,
                         IdentityApi identityApi,
                         MerchantApi merchantApi,
                         ReviewOrderApi reviewOrderApi,
                         ReviewRepository reviews,
                         ApplicationEventPublisher events) {
        this.currentUserApi = currentUserApi;
        this.identityApi = identityApi;
        this.merchantApi = merchantApi;
        this.reviewOrderApi = reviewOrderApi;
        this.reviews = reviews;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ReviewFormView prepareReview(Long orderItemId, Long productId) {
        UserSummary buyer = requireActiveBuyer();
        ReviewableOrderItem item = reviewOrderApi.requireReviewableItem(orderItemId, productId);
        requireContractBuyer(buyer, item);
        CartProductView product = findProduct(productId);
        boolean alreadyReviewed = reviews.existsByOrderItemId(item.orderItemId());
        return new ReviewFormView(
                item.orderItemId(), item.productId(), product.productName(), product.imageUrl(), alreadyReviewed);
    }

    @Transactional
    public Review submit(ReviewForm form) {
        UserSummary buyer = requireActiveBuyer();
        if (form == null || form.orderItemId() == null || form.productId() == null
                || form.rating() == null || form.rating() < 1 || form.rating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Thông tin đánh giá không hợp lệ.");
        }

        ReviewableOrderItem item = reviewOrderApi.requireReviewableItem(form.orderItemId(), form.productId());
        requireContractBuyer(buyer, item);
        CartProductView product = findProduct(form.productId());
        if (!Objects.equals(item.shopId(), product.shopId())) {
            throw new IllegalStateException("Ordering and Merchant contracts returned different shop ownership.");
        }
        MerchantShopSummary shop = merchantApi.findShopSummary(item.shopId())
                .orElseThrow(() -> new IllegalStateException("Reviewable order item has no Merchant shop."));

        if (reviews.existsByOrderItemId(item.orderItemId())) {
            throw new ReviewAlreadyExistsException();
        }

        String comment = normalizeComment(form.comment());
        Review review;
        try {
            review = reviews.saveAndFlush(Review.create(
                    buyer.userId(), item.productId(), item.orderItemId(), form.rating(), comment));
        } catch (DataIntegrityViolationException exception) {
            if (hasOrderItemConstraint(exception)) {
                throw new ReviewAlreadyExistsException();
            }
            throw exception;
        }

        events.publishEvent(new ReviewCreatedEvent(
                UUID.randomUUID(),
                review.getId(),
                buyer.userId(),
                item.productId(),
                item.orderItemId(),
                item.shopId(),
                shop.ownerId(),
                review.getRating(),
                Objects.requireNonNull(review.getCreatedAt(), "Persisted review must have a creation time.")));
        return review;
    }

    @Transactional(readOnly = true)
    public ReviewPageView getProductReviews(Long productId, int requestedPage) {
        if (productId == null || productId <= 0 || requestedPage < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sản phẩm hoặc trang đánh giá không hợp lệ.");
        }
        CartProductView product = findProduct(productId);
        if (!product.isSaleable()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm.");
        }
        Page<Review> page = reviews.findAllByProductIdOrderByCreatedAtDescIdDesc(
                productId, PageRequest.of(requestedPage - 1, REVIEWS_PER_PAGE));
        var items = page.getContent().stream()
                .map(this::toListItem)
                .toList();
        Double average = reviews.averageRatingForProduct(productId);
        BigDecimal averageRating = average == null
                ? BigDecimal.ZERO.setScale(1)
                : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
        return new ReviewPageView(
                productId,
                product.productName(),
                averageRating,
                page.getTotalElements(),
                items,
                page.getNumber() + 1,
                Math.max(page.getTotalPages(), 1));
    }

    private ReviewListItem toListItem(Review review) {
        UserSummary reviewer = identityApi.findUser(review.getUserId())
                .orElseThrow(() -> new IllegalStateException("Review references a missing identity."));
        return new ReviewListItem(
                review.getRating(),
                review.getComment(),
                reviewer.fullName(),
                review.getCreatedAt());
    }

    private CartProductView findProduct(Long productId) {
        return merchantApi.findProductForCart(productId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm."));
    }

    private UserSummary requireActiveBuyer() {
        UserSummary buyer = currentUserApi.requireCurrentUser();
        if (buyer.userId() == null || buyer.role() != UserRole.USER
                || buyer.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ người mua đang hoạt động mới được đánh giá.");
        }
        return buyer;
    }

    private static void requireContractBuyer(UserSummary buyer, ReviewableOrderItem item) {
        if (!Objects.equals(buyer.userId(), item.buyerId())) {
            throw new IllegalStateException("Ordering review contract returned an item for another buyer.");
        }
    }

    private static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.strip();
    }

    private static boolean hasOrderItemConstraint(Throwable exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && ORDER_ITEM_CONSTRAINT.equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
