package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.merchant.dto.CategoryOption;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/** Supplies active merchant categories to the shared site header. */
@ControllerAdvice(annotations = Controller.class)
public class MerchantHeaderModelAdvice {

    private final ObjectProvider<CategoryManagementService> categoryServiceProvider;

    public MerchantHeaderModelAdvice(ObjectProvider<CategoryManagementService> categoryServiceProvider) {
        this.categoryServiceProvider = categoryServiceProvider;
    }

    @ModelAttribute("headerCategories")
    public List<CategoryOption> headerCategories() {
        CategoryManagementService categoryService = categoryServiceProvider.getIfAvailable();
        if (categoryService == null) {
            return List.of();
        }
        return categoryService.listActiveForProductSelection().stream()
                .map(category -> new CategoryOption(category.getId(), category.getName()))
                .toList();
    }
}
