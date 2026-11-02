package com.senvia.doangiuaky.engagement.service;

import com.senvia.doangiuaky.engagement.dto.ProductReviewsPage;
import com.senvia.doangiuaky.engagement.repository.ReviewRepository;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.ordering.api.ReviewOrderApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(ReviewReadEventIntegrationTest.TestConfig.class)
@RecordApplicationEvents
class ReviewReadEventIntegrationTest {

    private static final long PRODUCT_ID = 987654321L;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private MerchantApi merchantApi;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ReviewService reviewService;

    @Test
    void displayingProductReviewsDoesNotPublishApplicationEvents() {
        when(merchantApi.findProductForCart(PRODUCT_ID))
                .thenReturn(Optional.of(new CartProductView(
                        PRODUCT_ID,
                        3L,
                        4L,
                        "Test Shop",
                        "Test Product",
                        null,
                        new BigDecimal("100.00"),
                        1,
                        true,
                        true,
                        true,
                        true)));
        when(reviewRepository.findByProductIdOrderByCreatedAtDescIdDesc(PRODUCT_ID))
                .thenReturn(List.of());
        long eventsBeforeRead = applicationEvents.stream().count();

        ProductReviewsPage page = reviewService.getProductReviews(PRODUCT_ID);

        assertEquals(0L, page.reviewCount());
        assertNull(page.averageRating());
        assertEquals(eventsBeforeRead, applicationEvents.stream().count());
    }

    @Configuration
    static class TestConfig {

        @Bean
        MerchantApi merchantApi() {
            return mock(MerchantApi.class);
        }

        @Bean
        ReviewRepository reviewRepository() {
            return mock(ReviewRepository.class);
        }

        @Bean
        ReviewOrderApi reviewOrderApi() {
            return mock(ReviewOrderApi.class);
        }

        @Bean
        ReviewService reviewService(
                ReviewOrderApi reviewOrderApi,
                ReviewRepository reviewRepository,
                MerchantApi merchantApi) {
            return new ReviewService(reviewOrderApi, reviewRepository, merchantApi);
        }
    }
}
