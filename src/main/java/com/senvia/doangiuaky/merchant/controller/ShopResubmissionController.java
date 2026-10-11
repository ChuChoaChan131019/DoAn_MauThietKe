package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.merchant.dto.ShopResubmissionForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.service.ShopNotFoundException;
import com.senvia.doangiuaky.merchant.service.ShopResubmissionAuthorizationException;
import com.senvia.doangiuaky.merchant.service.ShopResubmissionException;
import com.senvia.doangiuaky.merchant.service.ShopResubmissionService;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorageException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ShopResubmissionController {

    private final ShopResubmissionService resubmissionService;

    public ShopResubmissionController(ShopResubmissionService resubmissionService) {
        this.resubmissionService = resubmissionService;
    }

    @GetMapping("/merchant/shop-resubmission")
    public String form(@AuthenticationPrincipal IdentityPrincipal principal, Model model,
                       RedirectAttributes redirectAttributes) {
        try {
            Shop shop = resubmissionService.getRejectedShop(principal.getUserId());
            model.addAttribute("ownedShop", shop);
            if (!model.containsAttribute("shopResubmissionForm")) {
                model.addAttribute("shopResubmissionForm", ShopResubmissionForm.from(shop));
            }
            return "merchant/shop-resubmission";
        } catch (ShopNotFoundException exception) {
            return redirectWithError("/merchant/register", redirectAttributes,
                    exception.getMessage());
        } catch (ShopResubmissionException exception) {
            return redirectWithError("/merchant/shop-status", redirectAttributes,
                    exception.getMessage());
        } catch (ShopResubmissionAuthorizationException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @PostMapping("/merchant/shop-resubmission")
    public String submit(@AuthenticationPrincipal IdentityPrincipal principal,
                         @Valid @ModelAttribute("shopResubmissionForm") ShopResubmissionForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            try {
                model.addAttribute("ownedShop",
                        resubmissionService.getRejectedShop(principal.getUserId()));
                return "merchant/shop-resubmission";
            } catch (ShopNotFoundException | ShopResubmissionException exception) {
                return redirectWithError("/merchant/shop-status", redirectAttributes,
                        exception.getMessage());
            } catch (ShopResubmissionAuthorizationException exception) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        exception.getMessage(), exception);
            }
        }

        try {
            resubmissionService.resubmit(principal.getUserId(), form);
            redirectAttributes.addFlashAttribute(
                    "success", "Yêu cầu đã được cập nhật và gửi lại, đang chờ duyệt.");
            return "redirect:/merchant/shop-status";
        } catch (ShopNotFoundException | ShopResubmissionException
                 | MerchantImageStorageException exception) {
            model.addAttribute("resubmissionError", exception.getMessage());
            try {
                model.addAttribute("ownedShop",
                        resubmissionService.getRejectedShop(principal.getUserId()));
            } catch (RuntimeException ignored) {
                return redirectWithError("/merchant/shop-status", redirectAttributes,
                        exception.getMessage());
            }
            return "merchant/shop-resubmission";
        } catch (ShopResubmissionAuthorizationException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    exception.getMessage(), exception);
        }
    }

    private static String redirectWithError(String target, RedirectAttributes redirectAttributes,
                                            String message) {
        redirectAttributes.addFlashAttribute("error", message);
        return "redirect:" + target;
    }
}
