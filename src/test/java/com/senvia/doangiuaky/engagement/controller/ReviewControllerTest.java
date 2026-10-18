package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.dto.ReviewFormView;
import com.senvia.doangiuaky.engagement.dto.ReviewPageView;
import com.senvia.doangiuaky.engagement.service.ReviewService;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void anonymousCannotOpenReviewForm() throws Exception {
        mockMvc.perform(get("/account/review")
                        .param("orderItemId", "15")
                        .param("productId", "9"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void buyerFormUsesVerifiedOrderItemAndProductContext() throws Exception {
        when(reviewService.prepareReview(15L, 9L))
                .thenReturn(new ReviewFormView(15L, 9L, "Product", null, false));

        mockMvc.perform(get("/account/review")
                        .param("orderItemId", "15")
                        .param("productId", "9")
                        .with(user("buyer")))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/review-form"))
                .andExpect(model().attribute("reviewContext",
                        new ReviewFormView(15L, 9L, "Product", null, false)));
    }

    @Test
    void validReviewUsesPostRedirectGet() throws Exception {
        when(reviewService.prepareReview(15L, 9L))
                .thenReturn(new ReviewFormView(15L, 9L, "Product", null, false));

        mockMvc.perform(post("/account/review")
                        .with(user("buyer"))
                        .with(csrf())
                        .param("orderItemId", "15")
                        .param("productId", "9")
                        .param("rating", "5")
                        .param("comment", "Excellent"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/9/reviews"));

        verify(reviewService).submit(new ReviewForm(15L, 9L, 5, "Excellent"));
    }

    @Test
    void reviewListRendersAverageAndReviewsPage() throws Exception {
        ReviewPageView page = new ReviewPageView(9L, "Product", new BigDecimal("4.5"), 0L, List.of(), 1, 1);
        when(reviewService.getProductReviews(9L, 1)).thenReturn(page);

        mockMvc.perform(get("/products/9/reviews"))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/product-reviews"))
                .andExpect(model().attribute("reviewPage", page));
    }
}
