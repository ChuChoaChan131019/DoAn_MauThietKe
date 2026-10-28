package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.service.ReviewService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class ProductReviewsController {

    private final ReviewService reviewService;

    public ProductReviewsController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{productId}/reviews")
    public String productReviews(@PathVariable Long productId, Model model) {
        model.addAttribute("reviewPage", reviewService.getProductReviews(productId));
        return "engagement/product-reviews";
    }
}
