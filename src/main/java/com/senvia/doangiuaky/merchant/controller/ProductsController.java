package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.merchant.dto.CatalogProductCardView;
import com.senvia.doangiuaky.merchant.dto.CatalogFilterForm;
import com.senvia.doangiuaky.merchant.dto.CatalogFilterResult;
import com.senvia.doangiuaky.merchant.dto.CatalogProductDetailView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ProductsController {

    private static final int PAGE_SIZE = 12;

    private final DemoMerchantApi catalogService;
    private final CategoryManagementService categoryService;

    public ProductsController(DemoMerchantApi catalogService,
                              CategoryManagementService categoryService) {
        this.catalogService = catalogService;
        this.categoryService = categoryService;
    }

    @GetMapping("/products")
    public String listProducts(
            @Valid @ModelAttribute("filterForm") CatalogFilterForm filterForm,
            BindingResult bindingResult,
            @RequestParam(name = "brand", required = false) List<String> legacyBrand,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            Model model) {

        List<String> selectedShops = filterForm.getShop().isEmpty() && legacyBrand != null
                ? List.copyOf(legacyBrand)
                : filterForm.getShop();
        filterForm.setShop(selectedShops);
        List<Category> activeCategories = categoryService.listActiveForProductSelection();

        CatalogFilterResult filterResult = bindingResult.hasErrors()
                ? new CatalogFilterResult(List.of(), Map.of(), true)
                : catalogService.filterSaleableCatalogProducts(filterForm);
        if (!filterResult.categoryAvailable()) {
            // Do not echo a hidden or unknown category name back into the page.
            filterForm.setCategory(null);
            filterForm.setCategoryId(null);
            filterForm.setCategoryIds(List.of());
        } else if (!filterForm.getRequestedCategoryIds().isEmpty()
                || (filterForm.getCategory() != null
                && activeCategories.stream().noneMatch(category ->
                category.getName().equals(filterForm.getCategory().trim())))) {
            // IDs win over the legacy category name. Only echo an active legacy name.
            filterForm.setCategory(null);
        }
        List<Category> selectedCategories = filterResult.categoryAvailable()
                ? activeCategories.stream()
                .filter(category -> filterForm.getRequestedCategoryIds().contains(category.getId())
                        || (filterForm.getRequestedCategoryIds().isEmpty()
                        && filterForm.getCategory() != null
                        && category.getName().equals(filterForm.getCategory().trim())))
                .toList()
                : List.of();
        List<Long> selectedCategoryIds = selectedCategories.stream().map(Category::getId).toList();
        String catalogHeading = selectedCategories.size() == 1
                ? selectedCategories.getFirst().getName()
                : selectedCategories.isEmpty() ? "Tất cả sản phẩm"
                : "Sản phẩm thuộc " + selectedCategories.size() + " danh mục";
        boolean hasActiveFilters = !selectedCategories.isEmpty()
                || (filterForm.getQ() != null && !filterForm.getQ().isBlank())
                || filterForm.getMin() != null || filterForm.getMax() != null
                || !selectedShops.isEmpty();
        List<CatalogProductCardView> filteredProducts = filterResult.products().stream()
                .collect(Collectors.toCollection(java.util.ArrayList::new));

        if ("price_asc".equals(sort)) {
            filteredProducts.sort(Comparator.comparing(CatalogProductCardView::price));
        } else if ("price_desc".equals(sort)) {
            filteredProducts.sort(Comparator.comparing(CatalogProductCardView::price).reversed());
        }

        int totalItems = filteredProducts.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / PAGE_SIZE));
        int currentPage = Math.max(1, Math.min(page, totalPages));
        int start = Math.min((currentPage - 1) * PAGE_SIZE, totalItems);
        int end = Math.min(start + PAGE_SIZE, totalItems);

        model.addAttribute("products", filteredProducts.subList(start, end));
        model.addAttribute("totalItems", totalItems);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("sort", sort);
        model.addAttribute("selectedShops", selectedShops);
        model.addAttribute("categories", activeCategories);
        model.addAttribute("selectedCategoryIds", selectedCategoryIds);
        model.addAttribute("catalogHeading", catalogHeading);
        model.addAttribute("hasActiveFilters", hasActiveFilters);
        model.addAttribute("shopCounts", filterResult.shopCounts());
        model.addAttribute("filterForm", filterForm);
        model.addAttribute("filterErrors", bindingResult.hasErrors());
        model.addAttribute("filterErrorMessages", bindingResult.getAllErrors().stream()
                .map(this::localizedFilterError)
                .distinct()
                .toList());
        model.addAttribute("minErrors", fieldErrors(bindingResult, "min"));
        model.addAttribute("maxErrors", fieldErrors(bindingResult, "max"));
        model.addAttribute("rangeErrors", fieldErrors(bindingResult, "priceRangeValid"));
        model.addAttribute("categoryUnavailable", !filterResult.categoryAvailable());
        return "merchant/catalog/list";
    }

    private String localizedFilterError(ObjectError error) {
        if (error instanceof FieldError fieldError && fieldError.isBindingFailure()) {
            return switch (fieldError.getField()) {
                case "min" -> "Giá tối thiểu không đúng định dạng số.";
                case "max" -> "Giá tối đa không đúng định dạng số.";
                case "categoryId" -> "Danh mục không hợp lệ.";
                case "categoryIds" -> "Danh mục không hợp lệ.";
                case "q" -> "Từ khóa tìm kiếm không hợp lệ hoặc vượt quá 200 ký tự.";
                default -> "Một bộ lọc có định dạng không hợp lệ.";
            };
        }
        return error.getDefaultMessage();
    }

    private List<String> fieldErrors(BindingResult bindingResult, String field) {
        return bindingResult.getFieldErrors(field).stream()
                .map(this::localizedFilterError)
                .toList();
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model) {
        CatalogProductDetailView product = catalogService.findSaleableCatalogProduct(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy sản phẩm"));
        List<CatalogProductCardView> relatedProducts = catalogService.findSaleableCatalogProducts().stream()
                .filter(candidate -> !candidate.id().equals(id))
                .limit(4)
                .toList();
        model.addAttribute("product", product);
        model.addAttribute("relatedProducts", relatedProducts);
        return "products/detail";
    }
}
