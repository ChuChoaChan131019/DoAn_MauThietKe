package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.dto.ProductForm;
import com.senvia.doangiuaky.merchant.dto.SellerProductView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.entity.Product;
import com.senvia.doangiuaky.merchant.entity.ProductStatus;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductManagementServiceTest {

    private static final Long OWNER_ID = 20L;
    private static final Long SHOP_ID = 30L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryManagementService categoryService;

    @Mock
    private IdentityApi identityApi;

    @Mock
    private Shop approvedShop;

    private ProductManagementService service;

    @BeforeEach
    void setUp() {
        service = new ProductManagementService(
                productRepository, shopRepository, categoryRepository, categoryService, identityApi);
        lenient().when(identityApi.findUser(OWNER_ID))
                .thenReturn(Optional.of(new UserSummary(OWNER_ID, "Owner", UserRole.USER, AccountStatus.ACTIVE)));
        lenient().when(shopRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(approvedShop));
        lenient().when(shopRepository.findByOwnerIdForUpdate(OWNER_ID)).thenReturn(Optional.of(approvedShop));
        lenient().when(approvedShop.canAddProduct()).thenReturn(true);
    }

    @Test
    void createUsesOnlyAnActiveCategoryAndOwnedApprovedShop() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Category category = category(40L, "Active category", true);
        when(categoryService.requireActiveCategory(40L)).thenReturn(category);
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findById(40L)).thenReturn(Optional.of(category));

        SellerProductView result = service.create(OWNER_ID,
                new ProductForm("  Product  ", " Description ", 40L, new BigDecimal("10.00"), 3));

        assertEquals("Product", result.name());
        assertEquals(40L, result.categoryId());
        assertEquals(ProductStatus.ACTIVE, result.status());
        verify(categoryService).requireActiveCategory(40L);
        verify(shopRepository).findByOwnerIdForUpdate(OWNER_ID);
        verify(productRepository).saveAndFlush(any(Product.class));
    }

    @Test
    void createAssignsProductToShopResolvedFromOwner() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Category category = category(40L, "Active category", true);
        when(categoryService.requireActiveCategory(40L)).thenReturn(category);
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findById(40L)).thenReturn(Optional.of(category));

        service.create(OWNER_ID,
                new ProductForm("Product", "Description", 40L, new BigDecimal("10.00"), 3));

        var productCaptor = org.mockito.ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(productCaptor.capture());
        assertEquals(SHOP_ID, productCaptor.getValue().getShopId());
        assertEquals(40L, productCaptor.getValue().getCategoryId());
        assertEquals(ProductStatus.ACTIVE, productCaptor.getValue().getStatus());
    }

    @Test
    void nullOwnerIsRejectedBeforeLoadingShop() {
        assertThrows(ProductAuthorizationException.class,
                () -> service.create(null,
                        new ProductForm("Product", null, 40L, new BigDecimal("10.00"), 1)));

        verifyNoInteractions(identityApi, shopRepository, categoryService, productRepository, categoryRepository);
    }

    @Test
    void adminCannotCreateProducts() {
        when(identityApi.findUser(OWNER_ID))
                .thenReturn(Optional.of(new UserSummary(OWNER_ID, "Admin", UserRole.ADMIN, AccountStatus.ACTIVE)));

        assertThrows(ProductAuthorizationException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", null, 40L, new BigDecimal("10.00"), 1)));

        verifyNoInteractions(shopRepository, categoryService, productRepository, categoryRepository);
    }

    @Test
    void lockedAccountCannotCreateProducts() {
        when(identityApi.findUser(OWNER_ID))
                .thenReturn(Optional.of(new UserSummary(OWNER_ID, "Owner", UserRole.USER, AccountStatus.LOCKED)));

        assertThrows(ProductAuthorizationException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", null, 40L, new BigDecimal("10.00"), 1)));

        verifyNoInteractions(shopRepository, categoryService, productRepository, categoryRepository);
    }

    @Test
    void ownerWithoutShopCannotCreateProducts() {
        when(shopRepository.findByOwnerIdForUpdate(OWNER_ID)).thenReturn(Optional.empty());

        assertThrows(ProductAuthorizationException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", null, 40L, new BigDecimal("10.00"), 1)));

        verify(categoryService, never()).requireActiveCategory(any());
        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void hiddenCategoryIsRejectedBeforeProductIsSaved() {
        when(categoryService.requireActiveCategory(41L))
                .thenThrow(new InactiveCategoryException("Category hidden"));

        assertThrows(InactiveCategoryException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", null, 41L, new BigDecimal("10.00"), 1)));

        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void ownerCanMoveExistingProductToAnotherActiveCategory() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Product product = Product.create(SHOP_ID, 42L, "Product", null, new BigDecimal("10.00"), 2);
        Category targetCategory = category(43L, "Target", true);
        when(productRepository.findByIdAndShopId(50L, SHOP_ID)).thenReturn(Optional.of(product));
        when(categoryService.requireActiveCategory(43L)).thenReturn(targetCategory);
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findById(43L)).thenReturn(Optional.of(targetCategory));

        SellerProductView result = service.update(OWNER_ID, 50L,
                new ProductForm("Product", null, 43L, new BigDecimal("12.00"), 5));

        assertEquals(43L, product.getCategoryId());
        assertEquals(43L, result.categoryId());
        verify(productRepository).saveAndFlush(product);
    }

    @Test
    void ownerCannotUpdateProductFromAnotherShop() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        when(productRepository.findByIdAndShopId(999L, SHOP_ID)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> service.update(OWNER_ID, 999L,
                        new ProductForm("Product", null, 43L, new BigDecimal("12.00"), 5)));

        verify(categoryService, never()).requireActiveCategory(any());
    }

    @Test
    void lockedShopCannotCreateUpdateOrHideProducts() {
        when(approvedShop.canAddProduct()).thenReturn(false);
        ProductForm form = new ProductForm(
                "Product", null, 40L, new BigDecimal("10.00"), 1);

        assertThrows(ProductAuthorizationException.class,
                () -> service.create(OWNER_ID, form));
        assertThrows(ProductAuthorizationException.class,
                () -> service.update(OWNER_ID, 50L, form));
        assertThrows(ProductAuthorizationException.class,
                () -> service.hide(OWNER_ID, 50L));
        assertThrows(ProductAuthorizationException.class,
                () -> service.show(OWNER_ID, 50L));

        verifyNoInteractions(productRepository, categoryRepository, categoryService);
    }

    @Test
    void ownerCanShowOwnedHiddenProduct() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Product product = Product.create(SHOP_ID, 42L, "Product", null, new BigDecimal("10.00"), 2);
        product.hide();
        when(productRepository.findByIdAndShopId(50L, SHOP_ID)).thenReturn(Optional.of(product));

        service.show(OWNER_ID, 50L);

        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        verify(productRepository).saveAndFlush(product);
    }

    private static Category category(Long id, String name, boolean active) {
        Category category = org.mockito.Mockito.mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getName()).thenReturn(name);
        when(category.isActive()).thenReturn(active);
        return category;
    }
}
