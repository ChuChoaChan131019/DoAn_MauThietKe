package com.senvia.doangiuaky.merchant.controller;

import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.security.SecurityConfig;
import com.senvia.doangiuaky.merchant.dto.CategoryOption;
import com.senvia.doangiuaky.merchant.dto.SellerProductView;
import com.senvia.doangiuaky.merchant.entity.ProductStatus;
import com.senvia.doangiuaky.merchant.service.CategoryManagementService;
import com.senvia.doangiuaky.merchant.service.InactiveCategoryException;
import com.senvia.doangiuaky.merchant.service.ProductAuthorizationException;
import com.senvia.doangiuaky.merchant.service.ProductManagementService;
import com.senvia.doangiuaky.merchant.service.ProductStockConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ProductController.class)
@Import(SecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductManagementService productService;

    @MockitoBean
    private CategoryManagementService categoryService;

    private IdentityPrincipal ownerPrincipal;

    @BeforeEach
    void setUp() {
        ownerPrincipal = principal(1L);
        when(categoryService.listActiveForProductSelection())
                .thenReturn(List.of());
    }

    @Test
    void ownerCanOpenProductCreateForm() throws Exception {
        when(productService.listOwnedProducts(1L)).thenReturn(List.of());

        mockMvc.perform(get("/merchant/products/add").with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/product-add"))
                .andExpect(model().attributeExists("productForm", "categoryOptions"));
    }

    @Test
    void hiddenCategoryIsRejectedByProductCreateFlow() throws Exception {
        doThrow(new InactiveCategoryException("Danh mục đã bị ẩn."))
                .when(productService).create(eq(1L), any());

        mockMvc.perform(multipart("/merchant/products")
                        .with(user(ownerPrincipal))
                        .with(csrf())
                        .file(validImage())
                        .param("name", "Product")
                        .param("description", "Description")
                        .param("categoryId", "9")
                        .param("price", "10.00")
                        .param("stockQuantity", "2"))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/product-add"))
                .andExpect(model().attributeHasFieldErrors("productForm", "categoryId"));
    }

    @Test
    void validProductCreateRedirectsToOwnedProductList() throws Exception {
        when(productService.create(eq(1L), any()))
                .thenReturn(new SellerProductView(
                        11L, "Product", "Description", 9L, "Category", true,
                        new BigDecimal("10.00"), 2, ProductStatus.ACTIVE));

        mockMvc.perform(multipart("/merchant/products")
                        .with(user(ownerPrincipal))
                        .with(csrf())
                        .file(validImage())
                        .param("name", "Product")
                        .param("description", "Description")
                        .param("categoryId", "9")
                        .param("price", "10.00")
                        .param("stockQuantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merchant/products"))
                .andExpect(flash().attribute("successMessage", "Đã thêm sản phẩm thành công."));

        verify(productService).create(eq(1L), any());
    }

    @Test
    void invalidProductCreateRendersFormWithoutCallingService() throws Exception {
        mockMvc.perform(post("/merchant/products")
                        .with(user(ownerPrincipal))
                        .with(csrf())
                        .param("name", "")
                        .param("price", "0")
                        .param("stockQuantity", "-1"))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/product-add"))
                .andExpect(model().attributeHasFieldErrors(
                        "productForm", "name", "description", "categoryId", "price", "stockQuantity", "images"));

        verify(productService, never()).create(eq(1L), any());
    }

    @Test
    void adminCannotPostProductCreate() throws Exception {
        mockMvc.perform(post("/merchant/products")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .param("name", "Product")
                        .param("categoryId", "9")
                        .param("price", "10.00")
                        .param("stockQuantity", "2"))
                .andExpect(status().isForbidden());

        verify(productService, never()).create(any(), any());
    }

    @Test
    void productCreateWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/merchant/products")
                        .with(user(ownerPrincipal))
                        .param("name", "Product")
                        .param("categoryId", "9")
                        .param("price", "10.00")
                        .param("stockQuantity", "2"))
                .andExpect(status().isForbidden());

        verify(productService, never()).create(any(), any());
    }

    @Test
    void ownerCanOpenExistingProductForEdit() throws Exception {
        SellerProductView product = new SellerProductView(
                10L, "Product", "Description", 9L, "Category", true,
                new BigDecimal("10.00"), 2, ProductStatus.ACTIVE);
        when(productService.getOwnedProduct(1L, 10L)).thenReturn(product);

        mockMvc.perform(get("/merchant/products/10/edit").with(user(ownerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/product-edit"))
                .andExpect(model().attributeExists("productForm", "product"))
                .andExpect(model().attribute("productForm", org.hamcrest.Matchers.hasProperty(
                        "originalStockQuantity", org.hamcrest.Matchers.is(2))));
    }

    @Test
    void validProductUpdateRedirectsToOwnedProductList() throws Exception {
        mockMvc.perform(post("/merchant/products/10")
                        .with(user(ownerPrincipal))
                        .with(csrf())
                        .param("name", "Product updated")
                        .param("description", "Description")
                        .param("categoryId", "9")
                        .param("price", "10.00")
                        .param("stockQuantity", "2")
                        .param("originalStockQuantity", "2"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merchant/products"))
                .andExpect(flash().attribute("successMessage", "Đã cập nhật sản phẩm thành công."));

        verify(productService).updateWithImages(eq(1L), eq(10L), any());
    }

    @Test
    void staleStockShowsReloadMessageOnEditForm() throws Exception {
        SellerProductView product = new SellerProductView(
                10L, "Product", "Description", 9L, "Category", true,
                new BigDecimal("10.00"), 8, ProductStatus.ACTIVE);
        when(productService.getOwnedProduct(1L, 10L)).thenReturn(product);
        when(productService.updateWithImages(eq(1L), eq(10L), any()))
                .thenThrow(new ProductStockConflictException());

        mockMvc.perform(post("/merchant/products/10")
                        .with(user(ownerPrincipal))
                        .with(csrf())
                        .param("name", "Product")
                        .param("description", "Description")
                        .param("categoryId", "9")
                        .param("price", "10.00")
                        .param("stockQuantity", "10")
                        .param("originalStockQuantity", "10"))
                .andExpect(status().isOk())
                .andExpect(view().name("merchant/product-edit"))
                .andExpect(model().attributeHasFieldErrors("productForm", "stockQuantity"));
    }

    @Test
    void lockedShopCannotCreateProduct() throws Exception {
        when(productService.create(eq(1L), any()))
                .thenThrow(new ProductAuthorizationException("Gian hàng đang bị khóa."));

        mockMvc.perform(multipart("/merchant/products")
                        .with(user(ownerPrincipal))
                        .with(csrf())
                        .file(validImage())
                        .param("name", "Product")
                        .param("description", "Description")
                        .param("categoryId", "9")
                        .param("price", "10.00")
                        .param("stockQuantity", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockedShopCannotUpdateProduct() throws Exception {
        when(productService.updateWithImages(eq(1L), eq(10L), any()))
                .thenThrow(new ProductAuthorizationException("Gian hàng đang bị khóa."));

        mockMvc.perform(post("/merchant/products/10")
                        .with(user(ownerPrincipal))
                        .with(csrf())
                        .param("name", "Product")
                        .param("description", "Description")
                        .param("categoryId", "9")
                        .param("price", "10.00")
                        .param("stockQuantity", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    void lockedShopCannotHideProduct() throws Exception {
        doThrow(new ProductAuthorizationException("Gian hàng đang bị khóa."))
                .when(productService).hide(1L, 10L);

        mockMvc.perform(post("/merchant/products/10/hide")
                        .with(user(ownerPrincipal))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCanShowProduct() throws Exception {
        mockMvc.perform(post("/merchant/products/10/show")
                        .with(user(ownerPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/merchant/products"))
                .andExpect(flash().attribute("successMessage", "Đã bỏ ẩn sản phẩm thành công."));

        verify(productService).show(1L, 10L);
    }

    @Test
    void lockedShopCannotShowProduct() throws Exception {
        doThrow(new ProductAuthorizationException("Gian hàng đang bị khóa."))
                .when(productService).show(1L, 10L);

        mockMvc.perform(post("/merchant/products/10/show")
                        .with(user(ownerPrincipal))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousUserCannotOpenSellerProductFlow() throws Exception {
        mockMvc.perform(get("/merchant/products"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        verify(productService, never()).listOwnedProducts(any());
    }

    private static IdentityPrincipal principal(Long userId) {
        IdentityPrincipal principal = mock(IdentityPrincipal.class);
        when(principal.getUserId()).thenReturn(userId);
        when(principal.getAuthorities()).thenAnswer(ignored ->
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(principal.getUsername()).thenReturn("owner@example.com");
        when(principal.getPassword()).thenReturn("password");
        when(principal.isAccountNonExpired()).thenReturn(true);
        when(principal.isAccountNonLocked()).thenReturn(true);
        when(principal.isCredentialsNonExpired()).thenReturn(true);
        when(principal.isEnabled()).thenReturn(true);
        return principal;
    }

    private static MockMultipartFile validImage() {
        return new MockMultipartFile(
                "images",
                "product.png",
                "image/png",
                java.util.Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
    }
}
