package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.ProductReviewsPage;
import com.senvia.doangiuaky.engagement.service.ReviewService;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ProductReviewsController.class)
@Import(SecurityConfig.class)
class ProductReviewsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void guestCanSeeAverageAndPublicReviewDetails() throws Exception {
        when(reviewService.getProductReviews(19L)).thenReturn(new ProductReviewsPage(
                19L,
                "Senvia Product",
                "Senvia Shop",
                "https://example.test/product.png",
                new BigDecimal("4.33"),
                3,
                List.of(new ProductReviewsPage.ReviewItem(
                        (short) 5, "Đúng mô tả", Instant.parse("2026-01-02T03:04:05Z")))));

        mockMvc.perform(get("/products/19/reviews"))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/product-reviews"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("4.33 / 5")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("3 đánh giá")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đúng mô tả")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Người mua")));

        verify(reviewService).getProductReviews(19L);
    }

    @Test
    void guestSeesExplicitEmptyStateWithoutAnAverage() throws Exception {
        when(reviewService.getProductReviews(19L)).thenReturn(new ProductReviewsPage(
                19L, "Senvia Product", "Senvia Shop", null, null, 0, List.of()));

        mockMvc.perform(get("/products/19/reviews"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa có đánh giá nào")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hãy là người đầu tiên")));
    }

    @Test
    void unavailableProductReturnsNotFound() throws Exception {
        when(reviewService.getProductReviews(19L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/products/19/reviews"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidProductIdReturnsBadRequest() throws Exception {
        when(reviewService.getProductReviews(0L))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        mockMvc.perform(get("/products/0/reviews"))
                .andExpect(status().isBadRequest());
    }
}
