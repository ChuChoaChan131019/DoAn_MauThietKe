package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.dto.CatalogProductCardView;
import com.senvia.doangiuaky.merchant.dto.CatalogFilterForm;
import com.senvia.doangiuaky.merchant.dto.CatalogFilterResult;
import com.senvia.doangiuaky.merchant.dto.CatalogProductDetailView;
import com.senvia.doangiuaky.merchant.dto.PublicProductImageView;
import com.senvia.doangiuaky.merchant.dto.PublicShopView;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ProductsControllerTest {

    @Test
    void publicCatalogShowsSelectedActiveCategory() {
        DemoMerchantApi catalogService = mock(DemoMerchantApi.class);
        CategoryManagementService categoryService = mock(CategoryManagementService.class);
        Category activeCategory = mock(Category.class);
        when(activeCategory.getId()).thenReturn(1L);
        when(activeCategory.getName()).thenReturn("Active category");
        when(categoryService.listActiveForProductSelection()).thenReturn(List.of(activeCategory));
        when(catalogService.filterSaleableCatalogProducts(any(CatalogFilterForm.class)))
                .thenReturn(new CatalogFilterResult(List.of(), java.util.Map.of(), true));

        ExtendedModelMap model = new ExtendedModelMap();
        CatalogFilterForm filterForm = new CatalogFilterForm();
        filterForm.setCategoryId(1L);
        String view = new ProductsController(catalogService, categoryService).listProducts(
                filterForm, new BeanPropertyBindingResult(filterForm, "filterForm"),
                null, "newest", 1, model);

        assertEquals("merchant/catalog/list", view);
        assertEquals(List.of(1L), model.get("selectedCategoryIds"));
        assertEquals("Active category", model.get("catalogHeading"));
        assertEquals(true, model.get("hasActiveFilters"));
    }

    @Test
    void productDetailExposesDatabaseBackedDetailAndRelatedCards() {
        DemoMerchantApi catalogService = mock(DemoMerchantApi.class);
        CategoryManagementService categoryService = mock(CategoryManagementService.class);
        CatalogProductDetailView detail = new CatalogProductDetailView(
                10L,
                "Canvas Tote Bag",
                new BigDecimal("1190000.00"),
                "/images/product-placeholder.svg",
                "Thời trang",
                20L,
                "NOLA Store",
                "Túi tote",
                45,
                List.of(new PublicProductImageView("/images/product-placeholder.svg", 0)),
                new PublicShopView(20L, "NOLA Store", "Mô tả", null, "Địa chỉ"));
        when(catalogService.findSaleableCatalogProduct(10L)).thenReturn(java.util.Optional.of(detail));
        when(catalogService.findSaleableCatalogProducts()).thenReturn(List.of(
                new CatalogProductCardView(10L, "Canvas Tote Bag", new BigDecimal("1190000.00"),
                        "/images/product-placeholder.svg", "Thời trang", 20L, "NOLA Store"),
                new CatalogProductCardView(11L, "Leather Wallet", new BigDecimal("950000.00"),
                        "/images/product-placeholder.svg", "Thời trang", 20L, "NOLA Store")));

        ExtendedModelMap model = new ExtendedModelMap();
        String view = new ProductsController(catalogService, categoryService).productDetail(10L, model);

        assertEquals("products/detail", view);
        assertEquals(detail, model.get("product"));
        assertEquals(1, ((List<?>) model.get("relatedProducts")).size());
    }

    @Test
    void unavailableProductReturnsNotFound() {
        DemoMerchantApi catalogService = mock(DemoMerchantApi.class);
        CategoryManagementService categoryService = mock(CategoryManagementService.class);
        when(catalogService.findSaleableCatalogProduct(999L)).thenReturn(java.util.Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> new ProductsController(catalogService, categoryService)
                        .productDetail(999L, new ExtendedModelMap()));

        assertEquals(404, exception.getStatusCode().value());
    }
}
