package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.NewsletterSignupForm;
import com.senvia.doangiuaky.engagement.service.NewsletterSubscriptionService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class NewsletterController {

    private final NewsletterSubscriptionService subscriptions;

    public NewsletterController(NewsletterSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @PostMapping("/newsletter/subscribe")
    public String subscribe(@Valid @ModelAttribute NewsletterSignupForm form,
                            BindingResult bindingResult,
                            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("newsletterError",
                    bindingResult.getFieldError("email").getDefaultMessage());
            redirectAttributes.addFlashAttribute("newsletterEmail", form.getEmail());
            return "redirect:/#newsletter";
        }

        try {
            boolean created = subscriptions.subscribe(form.getEmail());
            redirectAttributes.addFlashAttribute(created ? "newsletterSuccess" : "newsletterError",
                    created ? "Đã ghi nhận email đăng ký nhận bản tin."
                            : "Email này đã đăng ký nhận bản tin.");
        } catch (DataIntegrityViolationException exception) {
            // The unique constraint also handles two simultaneous registrations for one email.
            redirectAttributes.addFlashAttribute("newsletterError", "Email này đã đăng ký nhận bản tin.");
        }
        return "redirect:/#newsletter";
    }
}
