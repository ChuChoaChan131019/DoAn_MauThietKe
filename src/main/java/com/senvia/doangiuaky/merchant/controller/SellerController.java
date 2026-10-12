package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.merchant.dto.ShopUpdateForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.service.ShopNotFoundException;
import com.senvia.doangiuaky.merchant.service.ShopProfileAuthorizationException;
import com.senvia.doangiuaky.merchant.service.ShopProfileException;
import com.senvia.doangiuaky.merchant.service.ShopProfileService;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorageException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class SellerController {

    private final ShopProfileService shopProfileService;

    public SellerController(ShopProfileService shopProfileService) {
        this.shopProfileService = shopProfileService;
    }

    @GetMapping("/seller")
    public String seller() {
        return "redirect:/merchant/dashboard";
    }

    @GetMapping("/merchant/shop-public")
    public String shopPublic() { return "merchant/shop-public"; }

    @GetMapping("/merchant/dashboard")
    public String merchantDashboard() { return "merchant/dashboard"; }

    public String merchantOrders() { return "ordering/seller-orders"; }

    
    public String merchantOrderDetail() { return "ordering/seller-order-detail"; }

    @GetMapping("/merchant/profile")
    public String merchantProfile(@AuthenticationPrincipal IdentityPrincipal principal, Model model,
                                  RedirectAttributes redirectAttributes) {
        try {
            Shop shop = shopProfileService.getOwnedApprovedShop(principal.getUserId());
            model.addAttribute("ownedShop", shop);
            if (!model.containsAttribute("shopUpdateForm")) {
                model.addAttribute("shopUpdateForm", ShopUpdateForm.from(shop));
            }
            return "merchant/shop-info";
        } catch (ShopNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/merchant/shop-status";
        } catch (ShopProfileAuthorizationException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    @PostMapping("/merchant/profile")
    public String updateMerchantProfile(@AuthenticationPrincipal IdentityPrincipal principal,
                                        @Valid @ModelAttribute("shopUpdateForm") ShopUpdateForm form,
                                        BindingResult bindingResult,
                                        Model model,
                                        RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderProfileAfterError(principal, form, model, redirectAttributes);
        }
        try {
            shopProfileService.update(principal.getUserId(), form);
            redirectAttributes.addFlashAttribute("success", "Đã cập nhật thông tin gian hàng.");
            return "redirect:/merchant/profile";
        } catch (ShopProfileAuthorizationException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        } catch (ShopProfileException | MerchantImageStorageException exception) {
            model.addAttribute("profileError", exception.getMessage());
            return renderProfileAfterError(principal, form, model, redirectAttributes);
        }
    }

    private String renderProfileAfterError(IdentityPrincipal principal, ShopUpdateForm form,
                                           Model model, RedirectAttributes redirectAttributes) {
        try {
            model.addAttribute("ownedShop", shopProfileService.getOwnedApprovedShop(principal.getUserId()));
            model.addAttribute("shopUpdateForm", form);
            return "merchant/shop-info";
        } catch (ShopNotFoundException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
            return "redirect:/merchant/shop-status";
        } catch (ShopProfileAuthorizationException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }
}
