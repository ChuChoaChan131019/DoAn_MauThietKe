package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.merchant.dto.CatalogProductCardView;
import com.senvia.doangiuaky.merchant.dto.CatalogProductDetailView;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
public class ProductsController {

    private static final int PAGE_SIZE = 6;

    private final DemoMerchantApi catalogService;
    private final CategoryManagementService categoryService;

    public ProductsController(DemoMerchantApi catalogService,
                              CategoryManagementService categoryService) {
        this.catalogService = catalogService;
        this.categoryService = categoryService;
    }

    @GetMapping("/products")
    public String listProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer min,
            @RequestParam(required = false) Integer max,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) List<String> shop,
            @RequestParam(name = "brand", required = false) List<String> legacyBrand,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            Model model) {

        List<String> selectedShops = shop != null ? shop : legacyBrand != null ? legacyBrand : List.of();
        List<CatalogProductCardView> catalogProducts = catalogService.findSaleableCatalogProducts();
        List<CatalogProductCardView> filteredProducts = catalogProducts.stream()
                .filter(product -> q == null || q.isBlank()
                        || product.name().toLowerCase().contains(q.trim().toLowerCase()))
                .filter(product -> min == null
                        || product.price().compareTo(BigDecimal.valueOf(min)) >= 0)
                .filter(product -> max == null
                        || product.price().compareTo(BigDecimal.valueOf(max)) <= 0)
                .filter(product -> category == null || category.isBlank()
                        || "Tất cả sản phẩm".equals(category)
                        || product.categoryName().equals(category))
                .filter(product -> selectedShops.isEmpty() || selectedShops.contains(product.shopName()))
                .collect(Collectors.toList());

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
        model.addAttribute("categories", categoryService.listActiveForProductSelection());

        Map<String, Long> shopCounts = catalogProducts.stream()
                .collect(Collectors.groupingBy(CatalogProductCardView::shopName, Collectors.counting()));
        model.addAttribute("shopCounts", shopCounts);
        model.addAttribute("q", q);
        model.addAttribute("min", min);
        model.addAttribute("max", max);
        model.addAttribute("category", category);
        return "products/list";
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
