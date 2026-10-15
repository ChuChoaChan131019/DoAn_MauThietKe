package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class HomeController {

    private static final String FALLBACK_CATEGORY_IMAGE =
            "https://images.unsplash.com/photo-1513694203232-719a280e022f?w=400&q=80";

    private static final Map<String, String> CATEGORY_IMAGES = Map.of(
            "Thời trang", "https://images.unsplash.com/photo-1584916201218-f4242ceb4809?w=400&q=80",
            "Làm đẹp", "https://images.unsplash.com/photo-1596462502278-27bf85033e5a?w=400&q=80",
            "Nhà cửa", "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?w=400&q=80",
            "Công nghệ", "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=400&q=80"
    );

    private final DemoMerchantApi catalogService;
    private final CategoryManagementService categoryService;

    public HomeController(DemoMerchantApi catalogService, CategoryManagementService categoryService) {
        this.catalogService = catalogService;
        this.categoryService = categoryService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("categories", categoryService.listActiveForProductSelection().stream()
                .map(HomeController::toCategoryCard)
                .toList());
        model.addAttribute("products", catalogService.findSaleableCatalogProducts());

        return "index";
    }

    private static Map<String, String> toCategoryCard(Category category) {
        return Map.of(
                "id", category.getId().toString(),
                "name", category.getName(),
                "img", CATEGORY_IMAGES.getOrDefault(category.getName(), FALLBACK_CATEGORY_IMAGE));
    }
}
