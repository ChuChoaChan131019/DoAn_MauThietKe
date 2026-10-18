package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.service.ReviewAlreadyExistsException;
import com.senvia.doangiuaky.engagement.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/products/{productId}/reviews")
    public String productReviews(@PathVariable Long productId,
                                 @RequestParam(defaultValue = "1") int page,
                                 Model model) {
        model.addAttribute("reviewPage", reviewService.getProductReviews(productId, page));
        return "engagement/product-reviews";
    }

    @GetMapping("/account/review")
    public String reviewForm(@RequestParam Long orderItemId,
                             @RequestParam Long productId,
                             Model model) {
        return renderReviewForm(new ReviewForm(orderItemId, productId, null, null), model);
    }

    @PostMapping("/account/review")
    public String submitReview(@Valid @ModelAttribute("reviewForm") ReviewForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderReviewForm(form, model);
        }
        try {
            reviewService.submit(form);
            redirectAttributes.addFlashAttribute("successMessage", "Cảm ơn bạn đã gửi đánh giá.");
        } catch (ReviewAlreadyExistsException exception) {
            redirectAttributes.addFlashAttribute("infoMessage", exception.getMessage());
        }
        return "redirect:/products/" + form.productId() + "/reviews";
    }

    private String renderReviewForm(ReviewForm form, Model model) {
        model.addAttribute("reviewForm", form);
        model.addAttribute("reviewContext",
                reviewService.prepareReview(form.orderItemId(), form.productId()));
        return "engagement/review-form";
    }
}
