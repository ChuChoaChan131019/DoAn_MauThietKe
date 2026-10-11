package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.merchant.dto.AdminCategoryView;
import com.senvia.doangiuaky.merchant.dto.CategoryForm;
import com.senvia.doangiuaky.merchant.service.CategoryAuthorizationException;
import com.senvia.doangiuaky.merchant.service.CategoryDuplicateNameException;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.CategoryNotFoundException;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/categories")
public class AdminCategoryController {

    private final CategoryManagementService categoryService;

    public AdminCategoryController(CategoryManagementService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal IdentityPrincipal principal,
                       @RequestParam(name = "edit", required = false) Long editId,
                       Model model) {
        try {
            return renderList(principal.getUserId(), editId, new CategoryForm(), model);
        } catch (CategoryNotFoundException exception) {
            throw notFound(exception);
        } catch (CategoryAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @PostMapping
    public String create(@AuthenticationPrincipal IdentityPrincipal principal,
                         @Valid @ModelAttribute("categoryForm") CategoryForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderInvalidCreate(principal.getUserId(), form, model);
        }

        try {
            categoryService.create(principal.getUserId(), form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã thêm danh mục thành công.");
            return "redirect:/admin/categories";
        } catch (CategoryDuplicateNameException exception) {
            bindingResult.rejectValue("name", "duplicate", exception.getMessage());
            return renderInvalidCreate(principal.getUserId(), form, model);
        } catch (CategoryAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable("id") Long categoryId,
                         @AuthenticationPrincipal IdentityPrincipal principal,
                         @Valid @ModelAttribute("editForm") CategoryForm form,
                         BindingResult bindingResult,
                         Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return renderInvalidEdit(principal.getUserId(), categoryId, form, model);
        }

        try {
            categoryService.update(principal.getUserId(), categoryId, form);
            redirectAttributes.addFlashAttribute("successMessage", "Đã cập nhật danh mục thành công.");
            return "redirect:/admin/categories";
        } catch (CategoryDuplicateNameException exception) {
            bindingResult.rejectValue("name", "duplicate", exception.getMessage());
            return renderInvalidEdit(principal.getUserId(), categoryId, form, model);
        } catch (CategoryNotFoundException exception) {
            throw notFound(exception);
        } catch (CategoryAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    @PostMapping("/{id}/hide")
    public String hide(@PathVariable("id") Long categoryId,
                       @AuthenticationPrincipal IdentityPrincipal principal,
                       RedirectAttributes redirectAttributes) {
        try {
            categoryService.hide(principal.getUserId(), categoryId);
            redirectAttributes.addFlashAttribute("successMessage", "Đã ẩn danh mục thành công.");
            return "redirect:/admin/categories";
        } catch (CategoryNotFoundException exception) {
            throw notFound(exception);
        } catch (CategoryAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    private String renderInvalidCreate(Long adminId, CategoryForm form, Model model) {
        try {
            return renderList(adminId, null, form, new CategoryForm(), model);
        } catch (CategoryAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    private String renderInvalidEdit(Long adminId, Long categoryId, CategoryForm form, Model model) {
        try {
            return renderList(adminId, categoryId, new CategoryForm(), form, model);
        } catch (CategoryNotFoundException exception) {
            throw notFound(exception);
        } catch (CategoryAuthorizationException exception) {
            throw forbidden(exception);
        }
    }

    private String renderList(Long adminId, Long editId, CategoryForm createForm, Model model) {
        CategoryForm editForm = new CategoryForm();
        if (editId != null) {
            AdminCategoryView category = categoryService.getForAdmin(adminId, editId);
            editForm = new CategoryForm(category.name(), category.description());
        }
        return renderList(adminId, editId, createForm, editForm, model);
    }

    private String renderList(Long adminId, Long editId, CategoryForm createForm,
                              CategoryForm editForm, Model model) {
        model.addAttribute("categories", categoryService.listAll(adminId));
        model.addAttribute("categoryForm", createForm);
        model.addAttribute("editForm", editForm);
        model.addAttribute("editingCategoryId", editId);
        return "merchant/admin-categories";
    }

    private static ResponseStatusException notFound(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
    }

    private static ResponseStatusException forbidden(RuntimeException exception) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
    }
}
