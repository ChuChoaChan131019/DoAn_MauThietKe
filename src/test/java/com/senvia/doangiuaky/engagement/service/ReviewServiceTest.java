package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.entity.Review;
import com.senvia.doangiuaky.engagement.repository.ReviewRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @InjectMocks
    private ReviewService reviewService;

    @Test
    void createsReviewUsingBuyerAndProductFromOrderingVerification() {
        ReviewableOrderItem verifiedItem = new ReviewableOrderItem(
                44L, 55L, "ORD-2026-001", 7L, 8L, 19L);
        when(reviewOrderApi.requireReviewableItem(44L, 19L)).thenReturn(verifiedItem);
        when(reviewRepository.existsByOrderItemId(44L)).thenReturn(false);
        ReviewForm form = form(44L, 19L, 5, "Rất tốt");

        assertEquals("ORD-2026-001", reviewService.createReview(form));

        ArgumentCaptor<Review> reviewCaptor = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).saveAndFlush(reviewCaptor.capture());
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
}
