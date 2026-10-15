package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.merchant.dto.ProductForm;
import com.senvia.doangiuaky.merchant.dto.ProductImageView;
import com.senvia.doangiuaky.merchant.dto.SellerProductView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.entity.Product;
import com.senvia.doangiuaky.merchant.entity.ProductImage;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductImageRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ProductManagementService {

    private static final int MAX_PRODUCT_IMAGES = 5;
    private static final String EXTERNAL_DEMO_IMAGE_PUBLIC_ID_PREFIX = "external-demo:";

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryManagementService categoryService;
    private final IdentityApi identityApi;
    private final MerchantImageStorage imageStorage;
    private final EntityManager entityManager;

    public ProductManagementService(ProductRepository productRepository,
                                    ProductImageRepository productImageRepository,
                                    ShopRepository shopRepository,
                                    CategoryRepository categoryRepository,
                                    CategoryManagementService categoryService,
                                    IdentityApi identityApi,
                                    MerchantImageStorage imageStorage,
                                    EntityManager entityManager) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.shopRepository = shopRepository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.identityApi = identityApi;
        this.imageStorage = imageStorage;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<SellerProductView> listOwnedProducts(Long ownerId) {
        Shop shop = requireCatalogMutableShop(ownerId);
        return productRepository.findAllByShopIdOrderByIdAsc(shop.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SellerProductView> pageOwnedProducts(Long ownerId, int requestedPage, int pageSize) {
        Shop shop = requireCatalogMutableShop(ownerId);
        PageRequest pageable = PageRequest.of(requestedPage - 1, pageSize, Sort.by("id").ascending());
        Page<Product> products = productRepository.findAllByShopId(shop.getId(), pageable);
        if (products.getTotalPages() > 0 && requestedPage > products.getTotalPages()) {
            products = productRepository.findAllByShopId(shop.getId(),
                    PageRequest.of(products.getTotalPages() - 1, pageSize, Sort.by("id").ascending()));
        }
        return products.map(this::toView);
    }

    @Transactional(readOnly = true)
    public SellerProductView getOwnedProduct(Long ownerId, Long productId) {
        Shop shop = requireCatalogMutableShop(ownerId);
        return toView(findOwnedProduct(productId, shop.getId()));
    }

    /**
     * Creates a product with all required product data and one to five uploaded images.
     * Cloudinary assets and database rows are compensated when any part of the use case fails.
     */
    @Transactional
    public SellerProductView create(Long ownerId, ProductForm form) {
        Shop shop = requireCatalogMutableShopForWrite(ownerId);
        Category category = categoryService.requireActiveCategory(form.getCategoryId());
        List<MultipartFile> files = uploadedFiles(form.getImages());
        requireAtLeastOneImage(files);
        validateImageCount(files, 0);

        List<MerchantImageAsset> uploaded = new ArrayList<>();
        Runnable rollbackCleanup = () -> deleteUploadedAssets(uploaded);
        try {
            List<MerchantImageAsset> assets = uploadProductImages(ownerId, files, uploaded);
            Product product = Product.create(
                    shop.getId(),
                    category.getId(),
                    form.getName(),
                    form.getDescription(),
                    form.getPrice(),
                    form.getStockQuantity());
            Product savedProduct = productRepository.saveAndFlush(product);
            saveImages(savedProduct, assets, 0);
            rollbackCleanup = registerRollbackCleanup(uploaded);
            return toView(savedProduct);
        } catch (RuntimeException exception) {
            rollbackCleanup.run();
            throw exception;
        }
    }

    @Transactional
    public SellerProductView update(Long ownerId, Long productId, ProductForm form) {
        Shop shop = requireCatalogMutableShop(ownerId);
        Product product = findOwnedProductForUpdate(productId, shop.getId());
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        requireUnchangedStock(product, form);
        Category category = categoryService.requireActiveCategory(form.getCategoryId());

        product.updateDetails(form.getName(), form.getDescription());
        product.changeCategory(category.getId());
        product.changePriceAndStock(form.getPrice(), form.getStockQuantity());
        return toView(productRepository.saveAndFlush(product));
    }

    @Transactional
    public SellerProductView updateWithImages(Long ownerId, Long productId, ProductForm form) {
        Shop shop = requireCatalogMutableShop(ownerId);
        Product product = findOwnedProduct(productId, shop.getId());
        Category category = categoryService.requireActiveCategory(form.getCategoryId());

        List<ProductImage> existingImages = productImageRepository
                .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId());
        Set<Long> removedImageIds = normalizeRemovedImageIds(form.getRemovedImageIds());
        List<ProductImage> removedImages = selectImagesToRemove(existingImages, removedImageIds);
        List<MultipartFile> newFiles = uploadedFiles(form.getImages());
        int remainingImageCount = existingImages.size() - removedImages.size() + newFiles.size();
        if (remainingImageCount <= 0) {
            throw new ProductImageManagementException(
                    "Sản phẩm phải còn ít nhất một ảnh sau khi cập nhật.");
        }
        validateImageCount(newFiles, existingImages.size() - removedImages.size());

        List<MerchantImageAsset> uploaded = new ArrayList<>();
        Runnable rollbackCleanup = () -> deleteUploadedAssets(uploaded);
        try {
            List<MerchantImageAsset> newAssets = uploadProductImages(ownerId, newFiles, uploaded);
            // Acquire the product lock after remote uploads, then reload data changed meanwhile.
            entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
            requireUnchangedStock(product, form);
            existingImages = productImageRepository
                    .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId());
            removedImages = selectImagesToRemove(existingImages, removedImageIds);
            if (existingImages.size() - removedImages.size() + newFiles.size() <= 0) {
                throw new ProductImageManagementException(
                        "Sản phẩm phải còn ít nhất một ảnh sau khi cập nhật.");
            }
            validateImageCount(newFiles, existingImages.size() - removedImages.size());
            product.updateDetails(form.getName(), form.getDescription());
            product.changeCategory(category.getId());
            product.changePriceAndStock(form.getPrice(), form.getStockQuantity());
            productRepository.saveAndFlush(product);

            if (!removedImages.isEmpty()) {
                productImageRepository.deleteAllInBatch(removedImages);
                productImageRepository.flush();
            }
            int nextOrder = existingImages.stream()
                    .mapToInt(ProductImage::getDisplayOrder)
                    .max()
                    .orElse(-1) + 1;
            saveImages(product, newAssets, nextOrder);
            rollbackCleanup = registerRollbackCleanup(uploaded);
            registerAfterCommitCleanup(removedImages.stream()
                    .map(ProductImage::getPublicId)
                    .toList());
            return toView(product);
        } catch (RuntimeException exception) {
            rollbackCleanup.run();
            throw exception;
        }
    }

    @Transactional
    public void deleteImage(Long ownerId, Long productId, Long imageId) {
        Shop shop = requireCatalogMutableShop(ownerId);
        Product product = findOwnedProductForUpdate(productId, shop.getId());
        List<ProductImage> existingImages = productImageRepository
                .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId());
        if (existingImages.size() <= 1) {
            throw new ProductImageManagementException(
                    "Không thể xóa ảnh cuối cùng của sản phẩm.");
        }
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, product.getId())
                .orElseThrow(() -> new ProductImageManagementException(
                        "Không tìm thấy ảnh thuộc sản phẩm của bạn."));
        productImageRepository.delete(image);
        productImageRepository.flush();
        registerAfterCommitCleanup(List.of(image.getPublicId()));
    }

    @Transactional
    public void hide(Long ownerId, Long productId) {
        Shop shop = requireCatalogMutableShop(ownerId);
        Product product = findOwnedProductForUpdate(productId, shop.getId());
        product.hide();
        productRepository.saveAndFlush(product);
    }

    @Transactional
    public void show(Long ownerId, Long productId) {
        Shop shop = requireCatalogMutableShop(ownerId);
        Product product = findOwnedProductForUpdate(productId, shop.getId());
        product.show();
        productRepository.saveAndFlush(product);
    }

    private Shop requireCatalogMutableShop(Long ownerId) {
        return requireCatalogMutableShop(ownerId, false);
    }

    private Shop requireCatalogMutableShopForWrite(Long ownerId) {
        return requireCatalogMutableShop(ownerId, true);
    }

    private Shop requireCatalogMutableShop(Long ownerId, boolean forUpdate) {
        if (ownerId == null || identityApi.findUser(ownerId)
                .filter(user -> user.role() == UserRole.USER
                        && user.accountStatus() == AccountStatus.ACTIVE)
                .isEmpty()) {
            throw new ProductAuthorizationException(
                    "Chỉ tài khoản người bán đang hoạt động mới được quản lý sản phẩm.");
        }

        Shop shop = (forUpdate
                ? shopRepository.findByOwnerIdForUpdate(ownerId)
                : shopRepository.findByOwnerId(ownerId))
                .orElseThrow(() -> new ProductAuthorizationException("Bạn chưa có gian hàng."));
        if (!shop.canAddProduct()) {
            throw new ProductAuthorizationException(
                    "Chỉ gian hàng đã được duyệt và không bị khóa mới được quản lý sản phẩm.");
        }
        return shop;
    }

    private Product findOwnedProduct(Long productId, Long shopId) {
        if (productId == null || productId <= 0) {
            throw new ProductNotFoundException("Không tìm thấy sản phẩm.");
        }
        return productRepository.findByIdAndShopId(productId, shopId)
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm thuộc gian hàng của bạn."));
    }

    private Product findOwnedProductForUpdate(Long productId, Long shopId) {
        if (productId == null || productId <= 0) {
            throw new ProductNotFoundException("Không tìm thấy sản phẩm.");
        }
        return productRepository.findByIdForUpdate(productId)
                .filter(product -> product.getShopId().equals(shopId))
                .orElseThrow(() -> new ProductNotFoundException("Không tìm thấy sản phẩm thuộc gian hàng của bạn."));
    }

    private static void requireUnchangedStock(Product product, ProductForm form) {
        if (form.getOriginalStockQuantity() == null
                || form.getOriginalStockQuantity() != product.getStockQuantity()) {
            throw new ProductStockConflictException();
        }
    }

    private SellerProductView toView(Product product) {
        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
        String categoryName = category == null ? "Không xác định" : category.getName();
        List<ProductImage> persistedImages = product.getId() == null ? List.of()
                : java.util.Optional.ofNullable(productImageRepository
                        .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId()))
                .orElseGet(List::of);
        List<ProductImageView> images = persistedImages
                .stream()
                .map(image -> new ProductImageView(
                        image.getId(), image.getImageUrl(), image.getDisplayOrder()))
                .toList();
        return new SellerProductView(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategoryId(),
                categoryName,
                category != null && category.isActive(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStatus(),
                images);
    }

    private List<MultipartFile> uploadedFiles(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .toList();
    }

    private void requireAtLeastOneImage(List<MultipartFile> files) {
        if (files.isEmpty()) {
            throw new ProductImageManagementException(
                    "Sản phẩm phải có ít nhất một ảnh.");
        }
    }

    private void validateImageCount(List<MultipartFile> newFiles, int existingCount) {
        if (existingCount + newFiles.size() > MAX_PRODUCT_IMAGES) {
            throw new ProductImageManagementException(
                    "Mỗi sản phẩm chỉ được có tối đa " + MAX_PRODUCT_IMAGES + " ảnh.");
        }
    }

    private List<MerchantImageAsset> uploadProductImages(Long ownerId,
                                                          List<MultipartFile> files,
                                                          List<MerchantImageAsset> uploaded) {
        for (MultipartFile file : files) {
            MerchantImageAsset asset = imageStorage.uploadProductImage(ownerId, file);
            if (asset == null || !StringUtils.hasText(asset.secureUrl())
                    || !StringUtils.hasText(asset.publicId())) {
                throw new ProductImageManagementException(
                        "Cloudinary không trả về thông tin ảnh hợp lệ.");
            }
            uploaded.add(asset);
        }
        return uploaded;
    }

    private void saveImages(Product product, List<MerchantImageAsset> assets, int firstOrder) {
        if (assets.isEmpty()) {
            return;
        }
        List<ProductImage> images = new ArrayList<>();
        for (int index = 0; index < assets.size(); index++) {
            MerchantImageAsset asset = assets.get(index);
            images.add(ProductImage.create(product, asset.secureUrl(), asset.publicId(), firstOrder + index));
        }
        productImageRepository.saveAllAndFlush(images);
    }

    private Set<Long> normalizeRemovedImageIds(List<Long> imageIds) {
        if (imageIds == null) {
            return Set.of();
        }
        Set<Long> normalized = new LinkedHashSet<>();
        for (Long imageId : imageIds) {
            if (imageId == null || imageId <= 0) {
                throw new ProductImageManagementException("Ảnh sản phẩm không hợp lệ.");
            }
            normalized.add(imageId);
        }
        return normalized;
    }

    private List<ProductImage> selectImagesToRemove(List<ProductImage> existingImages,
                                                     Set<Long> removedImageIds) {
        if (removedImageIds.isEmpty()) {
            return List.of();
        }
        Set<Long> existingIds = existingImages.stream()
                .map(ProductImage::getId)
                .collect(java.util.stream.Collectors.toSet());
        if (!existingIds.containsAll(removedImageIds)) {
            throw new ProductImageManagementException(
                    "Không thể xóa ảnh không thuộc sản phẩm của bạn.");
        }
        return existingImages.stream()
                .filter(image -> removedImageIds.contains(image.getId()))
                .toList();
    }

    private Runnable registerRollbackCleanup(Collection<MerchantImageAsset> assets) {
        if (assets.isEmpty()) {
            return () -> { };
        }
        AtomicBoolean cleanupAttempted = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (cleanupAttempted.compareAndSet(false, true)) {
                deleteUploadedAssets(assets);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        cleanup.run();
                    }
                }
            });
        }
        return cleanup;
    }

    private void registerAfterCommitCleanup(Collection<String> publicIds) {
        List<String> ids = publicIds.stream()
                .filter(StringUtils::hasText)
                .filter(publicId -> !publicId.startsWith(EXTERNAL_DEMO_IMAGE_PUBLIC_ID_PREFIX))
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        Runnable cleanup = () -> {
            for (String publicId : ids) {
                try {
                    imageStorage.delete(publicId);
                } catch (RuntimeException ignored) {
                    // Database state is already committed; cleanup failure must not undo it.
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        cleanup.run();
                    }
                }
            });
        } else {
            cleanup.run();
        }
    }

    private void deleteUploadedAssets(Collection<MerchantImageAsset> assets) {
        for (MerchantImageAsset asset : assets) {
            if (asset == null || !StringUtils.hasText(asset.publicId())) {
                continue;
            }
            try {
                imageStorage.delete(asset.publicId());
            } catch (RuntimeException ignored) {
                // Cleanup is best effort and must not mask the original failure.
            }
        }
    }
}
