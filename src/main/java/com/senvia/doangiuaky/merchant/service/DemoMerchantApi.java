package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.ShopOrderCapabilities;
import com.senvia.doangiuaky.merchant.dto.CatalogProductCardView;
import com.senvia.doangiuaky.merchant.dto.CatalogProductDetailView;
import com.senvia.doangiuaky.merchant.dto.PublicProductImageView;
import com.senvia.doangiuaky.merchant.dto.PublicShopView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.entity.Product;
import com.senvia.doangiuaky.merchant.entity.ProductImage;
import com.senvia.doangiuaky.merchant.entity.ProductStatus;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductImageRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Database-backed Merchant adapter used by Shopping and the public catalog UI.
 *
 * <p>Shopping continues to use the public {@link MerchantApi} methods. Catalog methods in this
 * class return view DTOs that deliberately omit JPA entities and internal moderation or
 * Cloudinary fields.</p>
 */
@Service
public class DemoMerchantApi implements MerchantApi {

    private static final String FALLBACK_IMAGE = "/images/product-placeholder.svg";

    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final CategoryRepository categoryRepository;
    private final ShopRepository shopRepository;
    private final IdentityApi identityApi;

    public DemoMerchantApi(
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            CategoryRepository categoryRepository,
            ShopRepository shopRepository,
            IdentityApi identityApi) {
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.categoryRepository = categoryRepository;
        this.shopRepository = shopRepository;
        this.identityApi = identityApi;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CartProductView> findProductForCart(Long productId) {
        if (productId == null || productId <= 0) {
            return Optional.empty();
        }
        return productRepository.findById(productId).flatMap(this::toProductReadContext)
                .map(ProductReadContext::cartProduct);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<CartProductView> findProductForCartMutation(Long productId) {
        if (productId == null || productId <= 0) {
            return Optional.empty();
        }
        return productRepository.findByIdForUpdate(productId).flatMap(this::toProductReadContext)
                .map(ProductReadContext::cartProduct);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ShopOrderCapabilities> findShopOrderCapabilities(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return Optional.empty();
        }
        return shopRepository.findById(shopId).map(shop -> {
            boolean ownerActive = identityApi.findUser(shop.getOwnerId())
                    .map(owner -> owner.accountStatus() == AccountStatus.ACTIVE)
                    .orElse(false);
            return new ShopOrderCapabilities(
                    shop.getId(),
                    ownerActive && shop.canReceiveOrder(),
                    shop.canHandleExistingOrders());
        });
    }

    @Transactional(readOnly = true)
    public List<CatalogProductCardView> findSaleableCatalogProducts() {
        return productRepository
                .findAllByStatusAndStockQuantityGreaterThanOrderByIdAsc(ProductStatus.ACTIVE, 0)
                .stream()
                .map(this::toSaleableCatalogProduct)
                .flatMap(Optional::stream)
                .map(this::toCardView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CatalogProductDetailView> findSaleableCatalogProduct(Long productId) {
        if (productId == null || productId <= 0) {
            return Optional.empty();
        }
        return productRepository.findById(productId).flatMap(this::toSaleableCatalogProduct);
    }

    @Transactional(readOnly = true)
    public Optional<PublicShopView> findPublicShop(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return Optional.empty();
        }
        return shopRepository.findById(shopId).flatMap(this::toPublicShopView);
    }

    @Transactional(readOnly = true)
    public Optional<PublicShopView> findPublicShopForOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            return Optional.empty();
        }
        return shopRepository.findByOwnerId(ownerId).flatMap(this::toPublicShopView);
    }

    @Transactional(readOnly = true)
    public List<CatalogProductCardView> findSaleableCatalogProductsByShop(Long shopId) {
        if (findPublicShop(shopId).isEmpty()) {
            return List.of();
        }
        return productRepository.findAllByShopIdOrderByIdAsc(shopId).stream()
                .map(this::toSaleableCatalogProduct)
                .flatMap(Optional::stream)
                .map(this::toCardView)
                .toList();
    }

    private Optional<CatalogProductDetailView> toSaleableCatalogProduct(Product product) {
        return toProductReadContext(product)
                .filter(context -> context.cartProduct().isSaleable())
                .map(this::toDetailView);
    }

    private Optional<ProductReadContext> toProductReadContext(Product product) {
        return shopRepository.findById(product.getShopId())
                .flatMap(shop -> categoryRepository.findById(product.getCategoryId())
                        .flatMap(category -> identityApi.findUser(shop.getOwnerId())
                                .map(owner -> {
                                    List<ProductImage> images = productImageRepository
                                            .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId());
                                    boolean ownerActive = owner.accountStatus() == AccountStatus.ACTIVE;
                                    boolean shopApproved = shop.getStatus() == ShopStatus.APPROVED;
                                    return new ProductReadContext(
                                            product,
                                            shop,
                                            category,
                                            images,
                                            ownerActive && shopApproved
                                                    ? toPublicShopView(shop, owner)
                                                    : null,
                                            new CartProductView(
                                                    product.getId(),
                                                    shop.getId(),
                                                    shop.getOwnerId(),
                                                    shop.getShopName(),
                                                    product.getName(),
                                                    firstImageUrl(images),
                                                    product.getPrice(),
                                                    product.getStockQuantity(),
                                                    ownerActive,
                                                    shopApproved,
                                                    category.isActive(),
                                                    product.getStatus() == ProductStatus.ACTIVE));
                                })));
    }

    private CatalogProductDetailView toDetailView(ProductReadContext context) {
        List<PublicProductImageView> images = publicImages(context.images());
        return new CatalogProductDetailView(
                context.product().getId(),
                context.product().getName(),
                context.product().getPrice(),
                images.get(0).imageUrl(),
                context.category().getName(),
                context.shop().getId(),
                context.shop().getShopName(),
                context.product().getDescription(),
                context.product().getStockQuantity(),
                images,
                context.publicShop());
    }

    private CatalogProductCardView toCardView(CatalogProductDetailView detail) {
        return new CatalogProductCardView(
                detail.id(),
                detail.name(),
                detail.price(),
                detail.thumbnailUrl(),
                detail.categoryName(),
                detail.shopId(),
                detail.shopName());
    }

    private Optional<PublicShopView> toPublicShopView(Shop shop) {
        if (shop.getStatus() != ShopStatus.APPROVED) {
            return Optional.empty();
        }
        return identityApi.findUser(shop.getOwnerId())
                .filter(owner -> owner.accountStatus() == AccountStatus.ACTIVE)
                .map(owner -> toPublicShopView(shop, owner));
    }

    private PublicShopView toPublicShopView(Shop shop, UserSummary owner) {
        return new PublicShopView(
                shop.getId(),
                shop.getShopName(),
                shop.getDescription(),
                shop.getLogoUrl(),
                shop.getAddress());
    }

    private List<PublicProductImageView> publicImages(List<ProductImage> productImages) {
        List<PublicProductImageView> images = productImages
                .stream()
                .map(image -> new PublicProductImageView(image.getImageUrl(), image.getDisplayOrder()))
                .toList();
        return images.isEmpty() ? List.of(new PublicProductImageView(FALLBACK_IMAGE, 0)) : images;
    }

    private String firstImageUrl(List<ProductImage> productImages) {
        return productImages
                .stream()
                .findFirst()
                .map(ProductImage::getImageUrl)
                .orElse(FALLBACK_IMAGE);
    }

    private record ProductReadContext(
            Product product,
            Shop shop,
            Category category,
            List<ProductImage> images,
            PublicShopView publicShop,
            CartProductView cartProduct) {
    }
}
