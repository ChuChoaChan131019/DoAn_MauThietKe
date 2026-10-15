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
import com.senvia.doangiuaky.merchant.entity.ProductImage;
import com.senvia.doangiuaky.merchant.repository.ProductImageRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ProductManagementServiceTest {

    private static final Long OWNER_ID = 20L;
    private static final Long SHOP_ID = 30L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ShopRepository shopRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private CategoryManagementService categoryService;

    @Mock
    private IdentityApi identityApi;

    @Mock
    private MerchantImageStorage imageStorage;

    @Mock
    private EntityManager entityManager;

    @Mock
    private Shop approvedShop;

    private ProductManagementService service;

    @BeforeEach
    void setUp() {
        service = new ProductManagementService(
                productRepository, productImageRepository, shopRepository, categoryRepository,
                categoryService, identityApi, imageStorage, entityManager);
        lenient().when(identityApi.findUser(OWNER_ID))
                .thenReturn(Optional.of(new UserSummary(OWNER_ID, "Owner", UserRole.USER, AccountStatus.ACTIVE)));
        lenient().when(shopRepository.findByOwnerId(OWNER_ID)).thenReturn(Optional.of(approvedShop));
        lenient().when(shopRepository.findByOwnerIdForUpdate(OWNER_ID)).thenReturn(Optional.of(approvedShop));
        lenient().when(approvedShop.canAddProduct()).thenReturn(true);
        lenient().when(imageStorage.uploadProductImage(eq(OWNER_ID), any()))
                .thenReturn(new MerchantImageAsset("https://cloudinary.test/default.png", "products/20/default"));
        lenient().when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(productImageRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
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
                productFormWithImages("  Product  ", 40L, image("one.png")));

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
        Category category = categoryWithId(40L);
        when(categoryService.requireActiveCategory(40L)).thenReturn(category);
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findById(40L)).thenReturn(Optional.of(category));

        service.create(OWNER_ID,
                productFormWithImages("Product", 40L, image("one.png")));

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
                        new ProductForm("Product", "Description", 40L, new BigDecimal("10.00"), 1)));

        verifyNoInteractions(identityApi, shopRepository, categoryService, productRepository, categoryRepository);
    }

    @Test
    void adminCannotCreateProducts() {
        when(identityApi.findUser(OWNER_ID))
                .thenReturn(Optional.of(new UserSummary(OWNER_ID, "Admin", UserRole.ADMIN, AccountStatus.ACTIVE)));

        assertThrows(ProductAuthorizationException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", "Description", 40L, new BigDecimal("10.00"), 1)));

        verifyNoInteractions(shopRepository, categoryService, productRepository, categoryRepository);
    }

    @Test
    void lockedAccountCannotCreateProducts() {
        when(identityApi.findUser(OWNER_ID))
                .thenReturn(Optional.of(new UserSummary(OWNER_ID, "Owner", UserRole.USER, AccountStatus.LOCKED)));

        assertThrows(ProductAuthorizationException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", "Description", 40L, new BigDecimal("10.00"), 1)));

        verifyNoInteractions(shopRepository, categoryService, productRepository, categoryRepository);
    }

    @Test
    void ownerWithoutShopCannotCreateProducts() {
        when(shopRepository.findByOwnerIdForUpdate(OWNER_ID)).thenReturn(Optional.empty());

        assertThrows(ProductAuthorizationException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", "Description", 40L, new BigDecimal("10.00"), 1)));

        verify(categoryService, never()).requireActiveCategory(any());
        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void hiddenCategoryIsRejectedBeforeProductIsSaved() {
        when(categoryService.requireActiveCategory(41L))
                .thenThrow(new InactiveCategoryException("Category hidden"));

        assertThrows(InactiveCategoryException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", "Description", 41L, new BigDecimal("10.00"), 1)));

        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void ownerCanMoveExistingProductToAnotherActiveCategory() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Product product = Product.create(SHOP_ID, 42L, "Product", "Description", new BigDecimal("10.00"), 2);
        Category targetCategory = category(43L, "Target", true);
        when(productRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(product));
        when(categoryService.requireActiveCategory(43L)).thenReturn(targetCategory);
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.findById(43L)).thenReturn(Optional.of(targetCategory));

        ProductForm form = new ProductForm("Product", "Description", 43L, new BigDecimal("12.00"), 5);
        form.setOriginalStockQuantity(2);
        SellerProductView result = service.update(OWNER_ID, 50L, form);

        assertEquals(43L, product.getCategoryId());
        assertEquals(43L, result.categoryId());
        verify(productRepository).saveAndFlush(product);
    }

    @Test
    void ownerCannotUpdateProductFromAnotherShop() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        when(productRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class,
                () -> service.update(OWNER_ID, 999L,
                        new ProductForm("Product", "Description", 43L, new BigDecimal("12.00"), 5)));

        verify(categoryService, never()).requireActiveCategory(any());
    }

    @Test
    void staleEditCannotRestoreStockDeductedByCheckout() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Product product = Product.create(SHOP_ID, 42L, "Product", "Description", new BigDecimal("10.00"), 8);
        when(productRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(product));
        ProductForm staleForm = new ProductForm("New description", "Changed", 42L,
                new BigDecimal("10.00"), 10);
        staleForm.setOriginalStockQuantity(10);

        assertThrows(ProductStockConflictException.class,
                () -> service.update(OWNER_ID, 50L, staleForm));

        assertEquals(8, product.getStockQuantity());
        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void lockedShopCannotCreateUpdateOrHideProducts() {
        when(approvedShop.canAddProduct()).thenReturn(false);
        ProductForm form = new ProductForm(
                "Product", "Description", 40L, new BigDecimal("10.00"), 1);

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
        Product product = Product.create(SHOP_ID, 42L, "Product", "Description", new BigDecimal("10.00"), 2);
        product.hide();
        when(productRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(product));

        service.show(OWNER_ID, 50L);

        assertEquals(ProductStatus.ACTIVE, product.getStatus());
        verify(productRepository).saveAndFlush(product);
    }

    @Test
    void createPersistsMultipleCloudinaryImagesInDisplayOrder() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Category category = categoryWithId(40L);
        when(categoryService.requireActiveCategory(40L)).thenReturn(category);
        when(imageStorage.uploadProductImage(eq(OWNER_ID), any()))
                .thenReturn(
                        new MerchantImageAsset("https://cloudinary.test/one.png", "products/20/one"),
                        new MerchantImageAsset("https://cloudinary.test/two.png", "products/20/two"));

        SellerProductView result = service.create(OWNER_ID,
                productFormWithImages("Product", 40L, image("one.png"), image("two.png")));

        assertEquals("Product", result.name());
        verify(imageStorage, times(2)).uploadProductImage(eq(OWNER_ID), any());
        var imageCaptor = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(productImageRepository).saveAllAndFlush(imageCaptor.capture());
        ProductImage persistedImage = (ProductImage) imageCaptor.getValue().get(0);
        assertEquals(2, imageCaptor.getValue().size());
        assertEquals("https://cloudinary.test/one.png", persistedImage.getImageUrl());
        assertEquals("products/20/one", persistedImage.getPublicId());
        assertEquals(0, persistedImage.getDisplayOrder());
        ProductImage secondImage = (ProductImage) imageCaptor.getValue().get(1);
        assertEquals("https://cloudinary.test/two.png", secondImage.getImageUrl());
        assertEquals("products/20/two", secondImage.getPublicId());
        assertEquals(1, secondImage.getDisplayOrder());
    }

    @Test
    void sellerPageIsScopedToOwnedShopAndClampsPastLastPage() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        PageRequest requested = PageRequest.of(9, 12, Sort.by("id").ascending());
        PageRequest last = PageRequest.of(2, 12, Sort.by("id").ascending());
        when(productRepository.findAllByShopId(SHOP_ID, requested))
                .thenReturn(new PageImpl<>(List.of(), requested, 25));
        when(productRepository.findAllByShopId(SHOP_ID, last))
                .thenReturn(new PageImpl<>(List.of(), last, 25));

        Page<SellerProductView> result = service.pageOwnedProducts(OWNER_ID, 10, 12);

        assertEquals(2, result.getNumber());
        assertEquals(3, result.getTotalPages());
        verify(productRepository).findAllByShopId(SHOP_ID, requested);
        verify(productRepository).findAllByShopId(SHOP_ID, last);
    }

    @Test
    void deletingExternalDemoImageDoesNotCallCloudinary() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Product product = Product.create(SHOP_ID, 42L, "Product", "Description", new BigDecimal("10.00"), 2);
        ProductImage externalImage = ProductImage.create(
                product, "https://images.unsplash.com/photo-test.jpg", "external-demo:501", 0);
        ProductImage cloudinaryImage = ProductImage.create(
                product, "https://cloudinary.test/second.png", "products/20/second", 1);
        when(productRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(product));
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(null))
                .thenReturn(List.of(externalImage, cloudinaryImage));
        when(productImageRepository.findByIdAndProductId(501L, null)).thenReturn(Optional.of(externalImage));

        service.deleteImage(OWNER_ID, 50L, 501L);

        verify(imageStorage, never()).delete("external-demo:501");
    }

    @Test
    void createCleansUploadedAssetsWhenDatabaseSaveFails() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Category category = categoryWithId(40L);
        when(categoryService.requireActiveCategory(40L)).thenReturn(category);
        when(imageStorage.uploadProductImage(eq(OWNER_ID), any()))
                .thenReturn(new MerchantImageAsset("https://cloudinary.test/one.png", "products/20/one"));
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenThrow(new IllegalStateException("database failure"));

        assertThrows(IllegalStateException.class, () -> service.create(OWNER_ID,
                productFormWithImages("Product", 40L, image("one.png"))));

        verify(imageStorage).delete("products/20/one");
    }

    @Test
    void createRejectsMissingImagesBeforeCallingCloudinary() {
        ProductImageManagementException exception = assertThrows(ProductImageManagementException.class,
                () -> service.create(OWNER_ID,
                        new ProductForm("Product", "Description", 40L, new BigDecimal("10.00"), 1)));

        assertEquals("Sản phẩm phải có ít nhất một ảnh.", exception.getMessage());
        verify(imageStorage, never()).uploadProductImage(any(), any());
        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void createCleansPreviouslyUploadedImagesWhenLaterUploadFails() {
        when(imageStorage.uploadProductImage(eq(OWNER_ID), any()))
                .thenReturn(new MerchantImageAsset("https://cloudinary.test/one.png", "products/20/one"))
                .thenThrow(new ProductImageManagementException("Ảnh thứ hai không hợp lệ."));

        assertThrows(ProductImageManagementException.class, () -> service.create(OWNER_ID,
                productFormWithImages("Product", 40L, image("one.png"), image("two.png"))));

        verify(imageStorage).delete("products/20/one");
        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void updateWithImagesRequiresOwnershipOfRemovedImagesAndCleansOldAssetAfterCommit() {
        when(approvedShop.getId()).thenReturn(SHOP_ID);
        Product product = org.mockito.Mockito.mock(Product.class);
        when(product.getId()).thenReturn(50L);
        when(product.getCategoryId()).thenReturn(40L);
        when(product.getName()).thenReturn("Product");
        when(product.getPrice()).thenReturn(new BigDecimal("10.00"));
        when(product.getStockQuantity()).thenReturn(1);
        when(product.getStatus()).thenReturn(ProductStatus.ACTIVE);
        ProductImage oldImage = org.mockito.Mockito.mock(ProductImage.class);
        when(oldImage.getId()).thenReturn(91L);
        when(oldImage.getPublicId()).thenReturn("products/20/old");
        when(oldImage.getDisplayOrder()).thenReturn(0);
        when(productRepository.findByIdAndShopId(50L, SHOP_ID)).thenReturn(Optional.of(product));
        Category category = categoryWithId(40L);
        when(categoryService.requireActiveCategory(40L)).thenReturn(category);
        when(productImageRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(50L))
                .thenReturn(List.of(oldImage));
        when(imageStorage.uploadProductImage(eq(OWNER_ID), any()))
                .thenReturn(new MerchantImageAsset("https://cloudinary.test/new.png", "products/20/new"));
        when(productRepository.saveAndFlush(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(productImageRepository.saveAllAndFlush(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ProductForm form = productFormWithImages("Updated", 40L, image("new.png"));
        form.setRemovedImageIds(List.of(999L));

        assertThrows(ProductImageManagementException.class,
                () -> service.updateWithImages(OWNER_ID, 50L, form));
        verify(imageStorage, never()).uploadProductImage(any(), any());

        form.setRemovedImageIds(List.of(91L));
        service.updateWithImages(OWNER_ID, 50L, form);

        verify(imageStorage).delete("products/20/old");
    }

    private static ProductForm productFormWithImages(String name, Long categoryId,
                                                      MockMultipartFile... images) {
        ProductForm form = new ProductForm(name, "Description", categoryId,
                new BigDecimal("10.00"), 1);
        form.setOriginalStockQuantity(1);
        form.setImages(List.of(images));
        return form;
    }

    private static MockMultipartFile image(String filename) {
        return new MockMultipartFile(
                "images",
                filename,
                "image/png",
                java.util.Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="));
    }

    private static Category category(Long id, String name, boolean active) {
        Category category = org.mockito.Mockito.mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getName()).thenReturn(name);
        when(category.isActive()).thenReturn(active);
        return category;
    }

    private static Category categoryWithId(Long id) {
        Category category = org.mockito.Mockito.mock(Category.class);
        when(category.getId()).thenReturn(id);
        return category;
    }
}
