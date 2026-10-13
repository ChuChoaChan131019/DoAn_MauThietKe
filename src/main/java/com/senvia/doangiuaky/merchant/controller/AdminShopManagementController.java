package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.merchant.dto.ShopLockForm;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.service.AdminShopManagementService;
import com.senvia.doangiuaky.merchant.service.ShopLockAuthorizationException;
import com.senvia.doangiuaky.merchant.service.ShopLockService;
import com.senvia.doangiuaky.merchant.service.ShopManagementNotFoundException;
import com.senvia.doangiuaky.merchant.state.InvalidShopStateTransitionException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/shops")
public class AdminShopManagementController {

    private final AdminShopManagementService managementService;
    private final ShopLockService lockService;

    public AdminShopManagementController(AdminShopManagementService managementService,
                                         ShopLockService lockService) {
        this.managementService = managementService;
        this.lockService = lockService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal IdentityPrincipal principal, Model model) {
        try {
            var shops = managementService.listManagedShops(principal.getUserId());
            model.addAttribute("shops", shops);
            model.addAttribute("approvedCount", shops.stream()
                    .filter(shop -> shop.status() == ShopStatus.APPROVED).count());
            model.addAttribute("lockedCount", shops.stream()
                    .filter(shop -> shop.status() == ShopStatus.LOCKED).count());
            return "merchant/admin/shops";
        } catch (ShopLockAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") Long shopId,
                         @AuthenticationPrincipal IdentityPrincipal principal,
                         Model model) {
        try {
            model.addAttribute("shop", managementService.getManagedShop(principal.getUserId(), shopId));
            if (!model.containsAttribute("shopLockForm")) {
                model.addAttribute("shopLockForm", new ShopLockForm());
            }
            return "merchant/admin/shop-detail";
        } catch (ShopManagementNotFoundException exception) {
            throw notFound(exception);
        } catch (ShopLockAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @PostMapping("/{id}/lock")
    public String lock(@PathVariable("id") Long shopId,
                       @AuthenticationPrincipal IdentityPrincipal principal,
                       @Valid @ModelAttribute("shopLockForm") ShopLockForm form,
                       BindingResult bindingResult,
                       Model model,
                       RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderInvalidLock(shopId, principal.getUserId(), model);
        }

        try {
            lockService.lock(shopId, principal.getUserId(), form.getReason());
            redirectAttributes.addFlashAttribute("successMessage", "Đã khóa gian hàng thành công.");
        } catch (ShopManagementNotFoundException exception) {
            throw notFound(exception);
        } catch (InvalidShopStateTransitionException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Chỉ gian hàng đang hoạt động mới được khóa.");
        } catch (ShopLockAuthorizationException exception) {
            throw forbidden(exception);
        }
        return "redirect:/admin/shops/" + shopId;
    }

    @PostMapping("/{id}/unlock")
    public String unlock(@PathVariable("id") Long shopId,
                         @AuthenticationPrincipal IdentityPrincipal principal,
                         RedirectAttributes redirectAttributes) {
        try {
            lockService.unlock(shopId, principal.getUserId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã mở khóa gian hàng thành công.");
        } catch (ShopManagementNotFoundException exception) {
            throw notFound(exception);
        } catch (InvalidShopStateTransitionException exception) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "Chỉ gian hàng đang bị khóa mới được mở khóa.");
        } catch (ShopLockAuthorizationException exception) {
            throw forbidden(exception);
        }
        return "redirect:/admin/shops/" + shopId;
    }

    private String renderInvalidLock(Long shopId, Long adminId, Model model) {
        try {
            model.addAttribute("shop", managementService.getManagedShop(adminId, shopId));
            return "merchant/admin/shop-detail";
        } catch (ShopManagementNotFoundException exception) {
            throw notFound(exception);
        } catch (ShopLockAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    private static ResponseStatusException notFound(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }

    private static ResponseStatusException forbidden(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }
}
