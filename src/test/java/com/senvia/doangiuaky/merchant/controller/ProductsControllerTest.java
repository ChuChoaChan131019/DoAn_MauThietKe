package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductsControllerTest {

    @Test
    void publicCatalogExposesOnlyActiveCategoriesForFiltering() {
        DemoMerchantApi catalogService = mock(DemoMerchantApi.class);
        CategoryManagementService categoryService = mock(CategoryManagementService.class);
        Category activeCategory = mock(Category.class);
        when(activeCategory.getName()).thenReturn("Active category");
        when(categoryService.listActiveForProductSelection()).thenReturn(List.of(activeCategory));
        when(catalogService.findSaleableCatalogProducts()).thenReturn(List.of());

        ExtendedModelMap model = new ExtendedModelMap();
        String view = new ProductsController(catalogService, categoryService).listProducts(
                null, null, null, null, null, "newest", 1, model);

        assertEquals("products/list", view);
        assertEquals(List.of(activeCategory), model.get("categories"));
    }
}
