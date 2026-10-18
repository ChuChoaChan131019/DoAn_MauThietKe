package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.api.ReviewCreatedEvent;
import com.senvia.doangiuaky.engagement.dto.ReviewForm;
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
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private final CurrentUserApi currentUserApi = mock(CurrentUserApi.class);
    private final IdentityApi identityApi = mock(IdentityApi.class);
    private final MerchantApi merchantApi = mock(MerchantApi.class);
    private final ReviewOrderApi reviewOrderApi = mock(ReviewOrderApi.class);
    private final ReviewRepository reviews = mock(ReviewRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final ReviewService service = new ReviewService(
            currentUserApi, identityApi, merchantApi, reviewOrderApi, reviews, events);

    @Test
    void createsReviewFromVerifiedOrderItemAndPublishesPublicEvent() {
        Instant createdAt = Instant.parse("2026-10-18T12:00:00Z");
        Review review = mock(Review.class);
        when(currentUserApi.requireCurrentUser()).thenReturn(activeBuyer());
        when(reviewOrderApi.requireReviewableItem(15L, 9L)).thenReturn(reviewableItem());
        when(merchantApi.findProductForCart(9L)).thenReturn(Optional.of(product()));
        when(merchantApi.findShopSummary(4L))
                .thenReturn(Optional.of(new MerchantShopSummary(4L, 8L, "Shop", true, true)));
        when(reviews.existsByOrderItemId(15L)).thenReturn(false);
        when(reviews.saveAndFlush(any(Review.class))).thenReturn(review);
        when(review.getId()).thenReturn(101L);
        when(review.getRating()).thenReturn((short) 5);
        when(review.getCreatedAt()).thenReturn(createdAt);

        assertEquals(review, service.submit(new ReviewForm(15L, 9L, 5, "  Rất tốt  ")));

        verify(reviews).saveAndFlush(any(Review.class));
        ArgumentCaptor<ReviewCreatedEvent> event = ArgumentCaptor.forClass(ReviewCreatedEvent.class);
        verify(events).publishEvent(event.capture());
        assertEquals(101L, event.getValue().reviewId());
        assertEquals(3L, event.getValue().reviewerId());
        assertEquals(9L, event.getValue().productId());
        assertEquals(15L, event.getValue().orderItemId());
        assertEquals(4L, event.getValue().shopId());
        assertEquals(8L, event.getValue().shopOwnerId());
        assertEquals(5, event.getValue().rating());
        assertEquals(createdAt, event.getValue().occurredAt());
        org.junit.jupiter.api.Assertions.assertNotNull(event.getValue().eventId());
    }

    @Test
    void refusesDuplicateReviewBeforePersistingAnotherOne() {
        when(currentUserApi.requireCurrentUser()).thenReturn(activeBuyer());
        when(reviewOrderApi.requireReviewableItem(15L, 9L)).thenReturn(reviewableItem());
        when(merchantApi.findProductForCart(9L)).thenReturn(Optional.of(product()));
        when(merchantApi.findShopSummary(4L))
                .thenReturn(Optional.of(new MerchantShopSummary(4L, 8L, "Shop", true, true)));
        when(reviews.existsByOrderItemId(15L)).thenReturn(true);

        assertThrows(ReviewAlreadyExistsException.class,
                () -> service.submit(new ReviewForm(15L, 9L, 4, null)));

        verify(reviews, never()).saveAndFlush(any(Review.class));
        verifyNoInteractions(events);
    }

    @Test
    void rejectsRatingsOutsideTheRequiredRange() {
        when(currentUserApi.requireCurrentUser()).thenReturn(activeBuyer());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.submit(new ReviewForm(15L, 9L, 6, "No")));

        assertEquals(400, exception.getStatusCode().value());
        verifyNoInteractions(reviewOrderApi, reviews, events);
    }

    @Test
    void returnsProductAverageAndReviewerNames() {
        Instant createdAt = Instant.parse("2026-10-18T12:00:00Z");
        Review review = mock(Review.class);
        when(merchantApi.findProductForCart(9L)).thenReturn(Optional.of(product()));
        when(reviews.findAllByProductIdOrderByCreatedAtDescIdDesc(eq(9L), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(review), PageRequest.of(0, 10), 1));
        when(reviews.averageRatingForProduct(9L)).thenReturn(4.5);
        when(review.getRating()).thenReturn((short) 5);
        when(review.getComment()).thenReturn("Good");
        when(review.getUserId()).thenReturn(3L);
        when(review.getCreatedAt()).thenReturn(createdAt);
        when(identityApi.findUser(3L)).thenReturn(Optional.of(
                new UserSummary(3L, "Buyer", UserRole.USER, AccountStatus.ACTIVE)));

        var result = service.getProductReviews(9L, 1);

        assertEquals(new BigDecimal("4.5"), result.averageRating());
        assertEquals(1, result.reviewCount());
        assertEquals("Buyer", result.reviews().getFirst().reviewerName());
        assertEquals(createdAt, result.reviews().getFirst().createdAt());
        verify(reviews).findAllByProductIdOrderByCreatedAtDescIdDesc(9L, PageRequest.of(0, 10));
    }

    private static UserSummary activeBuyer() {
        return new UserSummary(3L, "Buyer", UserRole.USER, AccountStatus.ACTIVE);
    }

    private static ReviewableOrderItem reviewableItem() {
        return new ReviewableOrderItem(15L, 10L, "ORD-1", 3L, 4L, 9L);
    }

    private static CartProductView product() {
        return new CartProductView(9L, 4L, 8L, "Shop", "Product", null,
                BigDecimal.TEN, 5, true, true, true, true);
    }
}
