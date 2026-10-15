package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.merchant.dto.CategoryOption;
import com.senvia.doangiuaky.merchant.dto.ProductForm;
import com.senvia.doangiuaky.merchant.dto.SellerProductView;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.CategoryNotFoundException;
import com.senvia.doangiuaky.merchant.service.InactiveCategoryException;
import com.senvia.doangiuaky.merchant.service.ProductAuthorizationException;
import com.senvia.doangiuaky.merchant.service.ProductImageManagementException;
import com.senvia.doangiuaky.merchant.service.ProductManagementService;
import com.senvia.doangiuaky.merchant.service.ProductNotFoundException;
import com.senvia.doangiuaky.merchant.service.ProductStockConflictException;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorageException;
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

import java.util.List;

@Controller
@RequestMapping("/merchant/products")
public class ProductController {

    private final ProductManagementService productService;
    private final CategoryManagementService categoryService;

    public ProductController(ProductManagementService productService,
                             CategoryManagementService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal IdentityPrincipal principal, Model model) {
        try {
            model.addAttribute("products", productService.listOwnedProducts(principal.getUserId()));
            return "merchant/products";
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @GetMapping("/add")
    public String addForm(@AuthenticationPrincipal IdentityPrincipal principal, Model model) {
        try {
            productService.listOwnedProducts(principal.getUserId());
            populateForm(model, new ProductForm());
            return "merchant/product-add";
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @PostMapping
    public String create(@AuthenticationPrincipal IdentityPrincipal principal,
                         @Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (form.getImages() == null || form.getImages().stream().noneMatch(file -> file != null && !file.isEmpty())) {
            bindingResult.rejectValue("images", "images", "Vui lòng chọn ít nhất một ảnh sản phẩm.");
        }
        if (bindingResult.hasErrors()) {
            return renderCreateForm(principal, form, model);
        }
        try {
            productService.create(principal.getUserId(), form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm sản phẩm thành công.");
            return "redirect:/merchant/products";
        } catch (InactiveCategoryException | CategoryNotFoundException exception) {
            bindingResult.rejectValue("categoryId", "category", exception.getMessage());
            return renderCreateForm(principal, form, model);
        } catch (ProductImageManagementException | MerchantImageStorageException exception) {
            bindingResult.rejectValue("images", "images", exception.getMessage());
            return renderCreateForm(principal, form, model);
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id,
                           @AuthenticationPrincipal IdentityPrincipal principal,
                           Model model) {
        try {
            SellerProductView product = productService.getOwnedProduct(principal.getUserId(), id);
            populateForm(model, toForm(product));
            model.addAttribute("product", product);
            return "merchant/product-edit";
        } catch (ProductNotFoundException exception) {
            throw notFound(exception);
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @AuthenticationPrincipal IdentityPrincipal principal,
                         @Valid @ModelAttribute("productForm") ProductForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderEditForm(principal, id, form, model);
        }
        try {
            productService.updateWithImages(principal.getUserId(), id, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật sản phẩm thành công.");
            return "redirect:/merchant/products";
        } catch (InactiveCategoryException | CategoryNotFoundException exception) {
            bindingResult.rejectValue("categoryId", "category", exception.getMessage());
            return renderEditForm(principal, id, form, model);
        } catch (ProductImageManagementException | MerchantImageStorageException exception) {
            bindingResult.rejectValue("images", "images", exception.getMessage());
            return renderEditForm(principal, id, form, model);
        } catch (ProductStockConflictException exception) {
            bindingResult.rejectValue("stockQuantity", "stockConflict", exception.getMessage());
            return renderEditForm(principal, id, form, model);
        } catch (ProductNotFoundException exception) {
            throw notFound(exception);
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @PostMapping("/{id}/hide")
    public String hide(@PathVariable Long id,
                       @AuthenticationPrincipal IdentityPrincipal principal,
                       RedirectAttributes redirectAttributes) {
        try {
            productService.hide(principal.getUserId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ẩn sản phẩm thành công.");
            return "redirect:/merchant/products";
        } catch (ProductNotFoundException exception) {
            throw notFound(exception);
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @PostMapping("/{id}/show")
    public String show(@PathVariable Long id,
                       @AuthenticationPrincipal IdentityPrincipal principal,
                       RedirectAttributes redirectAttributes) {
        try {
            productService.show(principal.getUserId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Đã bỏ ẩn sản phẩm thành công.");
            return "redirect:/merchant/products";
        } catch (ProductNotFoundException exception) {
            throw notFound(exception);
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    private String renderCreateForm(IdentityPrincipal principal, ProductForm form, Model model) {
        try {
            productService.listOwnedProducts(principal.getUserId());
            populateForm(model, form);
            return "merchant/product-add";
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    private String renderEditForm(IdentityPrincipal principal, Long productId,
                                  ProductForm form, Model model) {
        try {
            SellerProductView product = productService.getOwnedProduct(principal.getUserId(), productId);
            populateForm(model, form);
            model.addAttribute("product", product);
            return "merchant/product-edit";
        } catch (ProductNotFoundException exception) {
            throw notFound(exception);
        } catch (ProductAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    private void populateForm(Model model, ProductForm form) {
        List<CategoryOption> categoryOptions = categoryService.listActiveForProductSelection().stream()
                .map(category -> new CategoryOption(category.getId(), category.getName()))
                .toList();
        model.addAttribute("productForm", form);
        model.addAttribute("categoryOptions", categoryOptions);
    }

    private static ProductForm toForm(SellerProductView product) {
        ProductForm form = new ProductForm(product.name(), product.description(), product.categoryId(),
                product.price(), product.stockQuantity());
        form.setOriginalStockQuantity(product.stockQuantity());
        return form;
    }

    private static ResponseStatusException notFound(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }

    private static ResponseStatusException forbidden(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }
}
