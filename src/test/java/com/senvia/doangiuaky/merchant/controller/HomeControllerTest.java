package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.merchant.dto.CatalogProductView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HomeControllerTest {

    @Test
    void homeUsesActiveCategoriesAndSaleableCatalogProducts() {
        DemoMerchantApi catalogService = mock(DemoMerchantApi.class);
        CategoryManagementService categoryService = mock(CategoryManagementService.class);
        Category category = mock(Category.class);
        when(category.getName()).thenReturn("Nhà cửa");
        when(categoryService.listActiveForProductSelection()).thenReturn(List.of(category));
        when(catalogService.findSaleableCatalogProducts()).thenReturn(List.of(
                new CatalogProductView(1L, "Product", "Shop", new BigDecimal("10.00"),
                        false, 0, null, "image", "Description", 2, 0.0, 0, "Nhà cửa", List.of())));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = new HomeController(catalogService, categoryService).home(model);

        assertEquals("index", view);
        assertEquals(1, ((List<?>) model.get("categories")).size());
        assertEquals(1, ((List<?>) model.get("products")).size());
    }
}
