package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.service.ReviewService;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.ordering.api.ReviewableOrderItem;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ReviewController.class)
@Import(SecurityConfig.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void reviewFormIsAvailableOnlyAfterOrderingVerification() throws Exception {
        when(reviewService.requireReviewableItem(44L, 19L))
                .thenReturn(reviewableItem());

        mockMvc.perform(get("/account/review")
                        .with(user("buyer").roles("USER"))
                        .param("orderItemId", "44")
                        .param("productId", "19"))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/review-form"))
                .andExpect(model().attribute("reviewableItem", reviewableItem()))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đơn hàng ORD-2026-001")));

        verify(reviewService).requireReviewableItem(44L, 19L);
    }

    @Test
    void reviewFormIsNotShownWhenOrderingRejectsTheItem() throws Exception {
        when(reviewService.requireReviewableItem(44L, 19L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/account/review")
                        .with(user("buyer").roles("USER"))
                        .param("orderItemId", "44")
                        .param("productId", "19"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validSubmissionDelegatesReviewCreationAndRedirectsToOrder() throws Exception {
        when(reviewService.createReview(any(ReviewForm.class))).thenReturn("ORD-2026-001");

        mockMvc.perform(post("/account/review")
                        .with(user("buyer").roles("USER"))
                        .with(csrf())
                        .param("orderItemId", "44")
                        .param("productId", "19")
                        .param("rating", "5")
                        .param("comment", "Rất tốt"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/review/complete?orderCode=ORD-2026-001"));

        verify(reviewService).createReview(any(ReviewForm.class));
    }

    @Test
    void duplicateSubmissionReturnsConflictToTheBuyer() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Mục hàng này đã được đánh giá."))
                .when(reviewService).createReview(any(ReviewForm.class));

        mockMvc.perform(post("/account/review")
                        .with(user("buyer").roles("USER"))
                        .with(csrf())
                        .param("orderItemId", "44")
                        .param("productId", "19")
                        .param("rating", "5"))
                .andExpect(status().isConflict());
    }

    @Test
    void ratingOutsideRangeIsRejectedWithoutCreatingReview() throws Exception {
        when(reviewService.requireReviewableItem(44L, 19L)).thenReturn(reviewableItem());

        mockMvc.perform(post("/account/review")
                        .with(user("buyer").roles("USER"))
                        .with(csrf())
                        .param("orderItemId", "44")
                        .param("productId", "19")
                        .param("rating", "6"))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/review-form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Số sao phải từ 1 đến 5.")));

        verify(reviewService).requireReviewableItem(44L, 19L);
        verify(reviewService, never()).createReview(any(ReviewForm.class));
    }

    @Test
    void activeAdminIsRejectedByOrderingReviewVerification() throws Exception {
        when(reviewService.requireReviewableItem(44L, 19L))
                .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/account/review")
                        .with(user("admin").roles("ADMIN"))
                        .param("orderItemId", "44")
                        .param("productId", "19"))
                .andExpect(status().isForbidden());

        verify(reviewService).requireReviewableItem(44L, 19L);
    }

    @Test
    void unauthenticatedBuyerIsRedirectedToLogin() throws Exception {
        mockMvc.perform(get("/account/review")
                        .param("orderItemId", "44")
                        .param("productId", "19"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    private static ReviewableOrderItem reviewableItem() {
        return new ReviewableOrderItem(44L, 55L, "ORD-2026-001", 7L, 8L, 19L);
    }
}
