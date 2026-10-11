package com.senvia.doangiuaky.shopping.controller;

import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.shopping.dto.AddCartItemRequest;
import com.senvia.doangiuaky.shopping.dto.AddCartItemResult;
import com.senvia.doangiuaky.shopping.service.CartOperationException;
import com.senvia.doangiuaky.shopping.service.CartService;
import com.senvia.doangiuaky.shopping.service.FavoriteOperationException;
import com.senvia.doangiuaky.shopping.service.FavoriteService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;

import java.security.Principal;

@Controller
public class ShoppingController {

    private final CartService cartService;
    private final FavoriteService favoriteService;
    private final IdentityApi identityApi;

    public ShoppingController(
            CartService cartService,
            FavoriteService favoriteService,
            IdentityApi identityApi) {
        this.cartService = cartService;
        this.favoriteService = favoriteService;
        this.identityApi = identityApi;
    }

    @GetMapping("/cart")
    public String cart(Principal principal, Model model) {
        try {
            model.addAttribute("cart", cartService.getCart(currentUserId(principal)));
        } catch (CartOperationException exception) {
            model.addAttribute("cart", com.senvia.doangiuaky.shopping.dto.CartView.empty());
            model.addAttribute("cartError", exception.getMessage());
        }
        model.addAttribute("addCartItemRequest", new AddCartItemRequest());
        return "shopping/cart";
    }

    @PostMapping("/cart/items")
    public String addCartItem(
            Principal principal,
            @Valid @ModelAttribute("addCartItemRequest") AddCartItemRequest request,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("cartError", firstValidationError(bindingResult));
            return "redirect:/cart";
        }

        try {
            AddCartItemResult result = cartService.addItem(
                    currentUserId(principal), request.getProductId(), request.getQuantity());
            redirectAttributes.addFlashAttribute("cartSuccess", result.message());
        } catch (CartOperationException exception) {
            redirectAttributes.addFlashAttribute("cartError", exception.getMessage());
        }
        return "redirect:/cart";
    }

    @GetMapping("/account/wishlist")
    public String wishlist(Principal principal, Model model) {
        try {
            model.addAttribute("favorites", favoriteService.listForUser(currentUserId(principal)));
        } catch (FavoriteOperationException | CartOperationException exception) {
            model.addAttribute("favorites", java.util.List.of());
            model.addAttribute("favoriteError", exception.getMessage());
        }
        return "shopping/wishlist";
    }

    @PostMapping("/account/wishlist/{productId}")
    public String addFavorite(
            Principal principal,
            @PathVariable Long productId,
            RedirectAttributes redirectAttributes) {
        try {
            favoriteService.addFavorite(currentUserId(principal), productId);
            redirectAttributes.addFlashAttribute("favoriteSuccess", "Đã thêm sản phẩm vào yêu thích.");
        } catch (FavoriteOperationException | CartOperationException exception) {
            redirectAttributes.addFlashAttribute("favoriteError", exception.getMessage());
        }
        return "redirect:/account/wishlist";
    }

    @PostMapping("/account/wishlist/{productId}/remove")
    public String removeFavorite(
            Principal principal,
            @PathVariable Long productId,
            RedirectAttributes redirectAttributes) {
        try {
            favoriteService.removeFavorite(currentUserId(principal), productId);
            redirectAttributes.addFlashAttribute("favoriteSuccess", "Đã xóa sản phẩm khỏi yêu thích.");
        } catch (FavoriteOperationException | CartOperationException exception) {
            redirectAttributes.addFlashAttribute("favoriteError", exception.getMessage());
        }
        return "redirect:/account/wishlist";
    }

    private Long currentUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new CartOperationException("Bạn cần đăng nhập để tiếp tục.");
        }
        UserSummary user = identityApi.findUserByEmail(principal.getName())
                .orElseThrow(() -> new CartOperationException("Không xác định được tài khoản hiện tại."));
        return user.userId();
    }

    private static String firstValidationError(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null
                        ? "Dữ liệu giỏ hàng không hợp lệ."
                        : error.getDefaultMessage())
                .orElse("Dữ liệu giỏ hàng không hợp lệ.");
    }
}
