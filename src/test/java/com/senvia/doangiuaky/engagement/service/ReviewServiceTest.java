package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.dto.ProductReviewsPage;
import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.entity.Review;
import com.senvia.doangiuaky.engagement.repository.ReviewRepository;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.api.ReviewOrderApi;
import com.senvia.doangiuaky.ordering.api.ReviewableOrderItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewOrderApi reviewOrderApi;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private MerchantApi merchantApi;

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void returnsProductReviewsAndAverageWithoutExposingReviewOwnershipIds() {
        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.of(saleableProduct()));
        when(reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(19L)).thenReturn(List.of(
                Review.create(7L, 19L, 44L, 5, "Tốt"),
                Review.create(8L, 19L, 45L, 4, "Ổn"),
                Review.create(9L, 19L, 46L, 4, "Đúng mô tả")));

        ProductReviewsPage page = reviewService.getProductReviews(19L);

        assertEquals(19L, page.productId());
        assertEquals("Senvia Product", page.productName());
        assertEquals("Senvia Shop", page.shopName());
        assertEquals(new BigDecimal("4.33"), page.averageRating());
        assertEquals(3L, page.reviewCount());
        assertEquals(List.of(5, 4, 4), page.reviews().stream()
                .map(ProductReviewsPage.ReviewItem::rating)
                .map(Short::intValue)
                .toList());
        assertEquals(List.of("Tốt", "Ổn", "Đúng mô tả"), page.reviews().stream()
                .map(ProductReviewsPage.ReviewItem::comment)
                .toList());
        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any(Review.class));
        verify(reviewOrderApi, never()).requireReviewableItem(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void returnsExplicitEmptyReviewStateWhenProductHasNoReviews() {
        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.of(saleableProduct()));
        when(reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(19L)).thenReturn(List.of());

        ProductReviewsPage page = reviewService.getProductReviews(19L);

        assertEquals(0L, page.reviewCount());
        assertNull(page.averageRating());
        assertEquals(List.of(), page.reviews());
    }

    @Test
    void doesNotReadReviewsForMissingOrUnavailableProducts() {
        CartProductView unavailableProduct = new CartProductView(
                19L, 3L, 4L, "Senvia Shop", "Senvia Product", null,
                new BigDecimal("100.00"), 0, true, true, true, true);
        when(merchantApi.findProductForCart(19L)).thenReturn(Optional.of(unavailableProduct));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> reviewService.getProductReviews(19L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(reviewRepository, never()).findByProductIdOrderByCreatedAtDescIdDesc(19L);
    }

    @Test
    void rejectsInvalidProductIdBeforeCallingMerchantApi() {
        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> reviewService.getProductReviews(0L));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(merchantApi, never()).findProductForCart(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void createsReviewUsingBuyerAndProductFromOrderingVerification() {
        ReviewableOrderItem verifiedItem = new ReviewableOrderItem(
                44L, 55L, "ORD-2026-001", 7L, 8L, 19L);
        when(reviewOrderApi.requireReviewableItem(44L, 19L)).thenReturn(verifiedItem);
        when(reviewRepository.existsByOrderItemId(44L)).thenReturn(false);
        ReviewForm form = form(44L, 19L, 5, "Rất tốt");

        assertEquals("ORD-2026-001", reviewService.createReview(form));

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(reviewCaptor.capture());
        Review savedReview = reviewCaptor.getValue();
        assertEquals(7L, savedReview.getUserId());
        assertEquals(19L, savedReview.getProductId());
        assertEquals(44L, savedReview.getOrderItemId());
        assertEquals(5, savedReview.getRating());
        assertEquals("Rất tốt", savedReview.getComment());
    }

    @Test
    void doesNotSaveWhenOrderingRejectsWrongBuyerOrProduct() {
        when(reviewOrderApi.requireReviewableItem(44L, 19L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThrows(ResponseStatusException.class,
                () -> reviewService.createReview(form(44L, 19L, 4, null)));

        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any(Review.class));
    }

    @Test
    void doesNotSaveWhenOrderingRejectsInactiveOrNonBuyerAccount() {
        when(reviewOrderApi.requireReviewableItem(44L, 19L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> reviewService.createReview(form(44L, 19L, 4, null)));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any(Review.class));
    }

    @Test
    void rejectsIncompleteOrdersWhenOrderingReturnsConflict() {
        when(reviewOrderApi.requireReviewableItem(44L, 19L))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> reviewService.createReview(form(44L, 19L, 4, null)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any(Review.class));
    }

    @Test
    void invalidRatingIsRejectedAfterEligibilityVerificationAndBeforePersistence() {
        when(reviewOrderApi.requireReviewableItem(44L, 19L))
                .thenReturn(new ReviewableOrderItem(44L, 55L, "ORD-2026-001", 7L, 8L, 19L));

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> reviewService.createReview(form(44L, 19L, 6, null)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verify(reviewOrderApi).requireReviewableItem(44L, 19L);
        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any(Review.class));
    }

    @Test
    void duplicateOrderItemIsNotSavedTwice() {
        when(reviewOrderApi.requireReviewableItem(44L, 19L))
                .thenReturn(new ReviewableOrderItem(44L, 55L, "ORD-2026-001", 7L, 8L, 19L));
        when(reviewRepository.existsByOrderItemId(44L)).thenReturn(true);

        ResponseStatusException error = assertThrows(
                ResponseStatusException.class,
                () -> reviewService.createReview(form(44L, 19L, 4, null)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(reviewRepository, never()).save(org.mockito.ArgumentMatchers.any(Review.class));
    }

    private static ReviewForm form(Long orderItemId, Long productId, Integer rating, String comment) {
        ReviewForm form = new ReviewForm();
        form.setOrderItemId(orderItemId);
        form.setProductId(productId);
        form.setRating(rating);
        form.setComment(comment);
        return form;
    }

    private static CartProductView saleableProduct() {
        return new CartProductView(
                19L, 3L, 4L, "Senvia Shop", "Senvia Product", "https://example.test/product.png",
                new BigDecimal("100.00"), 5, true, true, true, true);
    }
}
