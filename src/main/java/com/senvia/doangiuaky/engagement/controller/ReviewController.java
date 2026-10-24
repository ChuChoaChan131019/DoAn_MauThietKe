package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.ReviewForm;
import com.senvia.doangiuaky.engagement.service.ReviewService;
import com.senvia.doangiuaky.ordering.api.ReviewableOrderItem;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/account/review")
    public String reviewForm(@RequestParam Long orderItemId,
                             @RequestParam Long productId,
                             Model model) {
        ReviewableOrderItem item = reviewService.requireReviewableItem(orderItemId, productId);
        model.addAttribute("reviewableItem", item);
        model.addAttribute("reviewForm", reviewForm(orderItemId, productId));
        return "engagement/review-form";
    }

    @PostMapping("/account/review")
    public String createReview(@Valid @ModelAttribute("reviewForm") ReviewForm form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            ReviewableOrderItem item = reviewService.requireReviewableItem(
                    form.getOrderItemId(), form.getProductId());
            model.addAttribute("reviewableItem", item);
            return "engagement/review-form";
        }

        String orderCode = reviewService.createReview(form);
        redirectAttributes.addAttribute("orderCode", orderCode);
        return "redirect:/account/review/complete";
    }

    @GetMapping("/account/review/complete")
    public String reviewComplete(@RequestParam String orderCode, Model model) {
        model.addAttribute("orderCode", orderCode);
        return "engagement/review-success";
    }

    private static ReviewForm reviewForm(Long orderItemId, Long productId) {
        ReviewForm form = new ReviewForm();
        form.setOrderItemId(orderItemId);
        form.setProductId(productId);
        return form;
    }
}
