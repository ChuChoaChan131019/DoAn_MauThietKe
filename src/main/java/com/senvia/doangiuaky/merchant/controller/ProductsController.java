package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.merchant.dto.CatalogFilterForm;
import com.senvia.doangiuaky.merchant.dto.CatalogFilterResult;
import com.senvia.doangiuaky.merchant.dto.CatalogProductCardView;
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

import java.util.ArrayList;
import java.util.List;

@Controller
public class ProductsController {

    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final List<Integer> PAGE_SIZES = List.of(12, 24, 48);

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
            @RequestParam(required = false, defaultValue = "1") String page,
            @RequestParam(required = false, defaultValue = "12") String size,
            Model model) {

        List<String> paginationErrors = new ArrayList<>();
        int requestedPage = parsePage(page, paginationErrors);
        int pageSize = parsePageSize(size, paginationErrors);
        List<String> selectedShops = filterForm.getShop().isEmpty() && legacyBrand != null
                ? List.copyOf(legacyBrand)
                : filterForm.getShop();
        filterForm.setShop(selectedShops);
        List<Category> activeCategories = categoryService.listActiveForProductSelection();

        CatalogFilterResult filterResult = bindingResult.hasErrors()
                ? CatalogFilterResult.empty(true, pageSize)
                : catalogService.filterSaleableCatalogProducts(filterForm, requestedPage, pageSize, sort);
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
        model.addAttribute("products", filterResult.products());
        model.addAttribute("totalItems", filterResult.totalItems());
        model.addAttribute("totalPages", filterResult.totalPages());
        model.addAttribute("currentPage", filterResult.currentPage());
        model.addAttribute("pageSize", filterResult.pageSize());
        model.addAttribute("pageSizes", PAGE_SIZES);
        model.addAttribute("sort", sort);
        model.addAttribute("selectedShops", selectedShops);
        model.addAttribute("categories", activeCategories);
        model.addAttribute("selectedCategoryIds", selectedCategoryIds);
        model.addAttribute("catalogHeading", catalogHeading);
        model.addAttribute("hasActiveFilters", hasActiveFilters);
        model.addAttribute("shopCounts", filterResult.shopCounts());
        model.addAttribute("filterForm", filterForm);
        model.addAttribute("filterErrors", bindingResult.hasErrors() || !paginationErrors.isEmpty());
        List<String> filterErrorMessages = new ArrayList<>(bindingResult.getAllErrors().stream()
                .map(this::localizedFilterError)
                .distinct()
                .toList());
        filterErrorMessages.addAll(paginationErrors);
        model.addAttribute("filterErrorMessages", filterErrorMessages);
        model.addAttribute("minErrors", fieldErrors(bindingResult, "min"));
        model.addAttribute("maxErrors", fieldErrors(bindingResult, "max"));
        model.addAttribute("rangeErrors", fieldErrors(bindingResult, "priceRangeValid"));
        model.addAttribute("categoryUnavailable", !filterResult.categoryAvailable());
        return "merchant/catalog/list";
    }

    private int parsePage(String rawPage, List<String> errors) {
        try {
            int parsed = Integer.parseInt(rawPage);
            if (parsed >= 1) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Render the catalog with a safe default and a visible validation error.
        }
        errors.add("Số trang phải là số nguyên lớn hơn hoặc bằng 1.");
        return 1;
    }

    private int parsePageSize(String rawSize, List<String> errors) {
        try {
            int parsed = Integer.parseInt(rawSize);
            if (PAGE_SIZES.contains(parsed)) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Render the catalog with a safe default and a visible validation error.
        }
        errors.add("Số sản phẩm mỗi trang phải là 12, 24 hoặc 48.");
        return DEFAULT_PAGE_SIZE;
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
