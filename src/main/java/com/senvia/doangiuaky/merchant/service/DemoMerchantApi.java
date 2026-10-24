package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.merchant.api.ShopOrderCapabilities;
import com.senvia.doangiuaky.merchant.dto.CatalogProductCardView;
import com.senvia.doangiuaky.merchant.dto.CatalogFilterForm;
import com.senvia.doangiuaky.merchant.dto.CatalogFilterResult;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchantShopSummary> findShopSummary(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return Optional.empty();
        }
        return shopRepository.findById(shopId).map(this::toShopSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchantShopSummary> findShopSummaryForOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            return Optional.empty();
        }
        return shopRepository.findByOwnerId(ownerId).map(this::toShopSummary);
    }

    private MerchantShopSummary toShopSummary(Shop shop) {
        return new MerchantShopSummary(
                shop.getId(), shop.getOwnerId(), shop.getShopName(),
                identityApi.findUser(shop.getOwnerId())
                        .map(owner -> owner.accountStatus() == AccountStatus.ACTIVE)
                        .orElse(false),
                shop.canBePubliclyListed());
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

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public CatalogFilterResult filterSaleableCatalogProducts(
            CatalogFilterForm filter, int requestedPage, int pageSize, String sort) {
        if (requestedPage < 1 || pageSize < 1 || pageSize > 48) {
            throw new IllegalArgumentException("Invalid catalog page or size");
        }
        String query = normalizeCatalogQuery(filter.getQ());
        List<Long> requestedCategoryIds = filter.getRequestedCategoryIds();
        boolean filterByLegacyCategory = requestedCategoryIds.isEmpty()
                && filter.getCategory() != null
                && !filter.getCategory().isBlank()
                && !"Tất cả sản phẩm".equals(filter.getCategory().trim());

        List<Category> activeCategories = categoryRepository.findAllByActiveTrueOrderByNameAscIdAsc();
        Map<Long, Category> activeCategoryById = activeCategories.stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        List<Long> categoryIds = activeCategories.stream().map(Category::getId).toList();
        if (!requestedCategoryIds.isEmpty()) {
            if (requestedCategoryIds.stream().anyMatch(id -> !activeCategoryById.containsKey(id))) {
                return CatalogFilterResult.empty(false, pageSize);
            }
            categoryIds = requestedCategoryIds;
        } else if (filterByLegacyCategory) {
            Long categoryId = activeCategories.stream()
                    .filter(category -> category.getName().equals(filter.getCategory().trim()))
                    .map(Category::getId)
                    .findFirst()
                    .orElse(null);
            if (categoryId == null) {
                return CatalogFilterResult.empty(false, pageSize);
            }
            categoryIds = List.of(categoryId);
        }

        List<Shop> publicShops = shopRepository.findAllByStatusInOrderByShopNameAscIdAsc(
                        Set.of(ShopStatus.APPROVED)).stream()
                .filter(Shop::canBePubliclyListed)
                .filter(shop -> identityApi.isUserActive(shop.getOwnerId()))
                .toList();
        if (categoryIds.isEmpty() || publicShops.isEmpty()) {
            return CatalogFilterResult.empty(true, pageSize);
        }
        Map<Long, Shop> publicShopById = publicShops.stream()
                .collect(Collectors.toMap(Shop::getId, Function.identity()));
        List<Long> publicShopIds = publicShops.stream().map(Shop::getId).toList();
        boolean filterByMin = filter.getMin() != null;
        BigDecimal min = filterByMin ? filter.getMin() : BigDecimal.ZERO;
        boolean filterByMax = filter.getMax() != null;
        BigDecimal max = filterByMax ? filter.getMax() : BigDecimal.ZERO;
        boolean filterByQuery = query != null;
        String escapedQuery = filterByQuery ? escapeLikeWildcards(query) : "";
        Map<String, Long> shopCounts = new HashMap<>();
        for (ProductRepository.CatalogShopCount count : productRepository.countCatalogProductsByShop(
                ProductStatus.ACTIVE, 0, publicShopIds, categoryIds,
                filterByMin, min, filterByMax, max, filterByQuery, escapedQuery)) {
            Shop shop = publicShopById.get(count.getShopId());
            if (shop != null) {
                shopCounts.merge(shop.getShopName(), count.getProductCount(), Long::sum);
            }
        }
        List<String> selectedShops = filter.getShop() == null ? List.of() : filter.getShop();
        List<Long> selectedShopIds = publicShops.stream()
                .filter(shop -> selectedShops.isEmpty() || selectedShops.contains(shop.getShopName()))
                .map(Shop::getId)
                .toList();
        long totalItems = selectedShops.isEmpty()
                ? shopCounts.values().stream().mapToLong(Long::longValue).sum()
                : shopCounts.entrySet().stream()
                .filter(entry -> selectedShops.contains(entry.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        if (totalItems == 0) {
            return new CatalogFilterResult(List.of(), shopCounts, true, 0, 1, 1, pageSize);
        }
        int totalPages = (int) ((totalItems - 1) / pageSize + 1);
        int currentPage = Math.min(requestedPage, totalPages);
        List<Product> pageProducts = productRepository.findCatalogPage(
                ProductStatus.ACTIVE, 0, selectedShopIds, categoryIds,
                filterByMin, min, filterByMax, max, filterByQuery, escapedQuery,
                PageRequest.of(currentPage - 1, pageSize, catalogSort(sort)));
        Map<Long, String> thumbnailByProductId = new HashMap<>();
        if (!pageProducts.isEmpty()) {
            for (ProductImage image : productImageRepository.findCatalogImages(
                    pageProducts.stream().map(Product::getId).toList())) {
                thumbnailByProductId.putIfAbsent(image.getProduct().getId(), image.getImageUrl());
            }
        }
        List<CatalogProductCardView> cards = pageProducts.stream()
                .map(product -> new CatalogProductCardView(
                        product.getId(), product.getName(), product.getPrice(),
                        thumbnailByProductId.getOrDefault(product.getId(), FALLBACK_IMAGE),
                        activeCategoryById.get(product.getCategoryId()).getName(),
                        product.getShopId(), publicShopById.get(product.getShopId()).getShopName()))
                .toList();
        return new CatalogFilterResult(cards, shopCounts, true, totalItems, totalPages, currentPage, pageSize);
    }

    private Sort catalogSort(String sort) {
        return switch (sort == null ? "newest" : sort) {
            case "price_asc" -> Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"));
            case "price_desc" -> Sort.by(Sort.Order.desc("price"), Sort.Order.asc("id"));
            default -> Sort.by(Sort.Order.asc("id"));
        };
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

    private String normalizeCatalogQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        return query.trim();
    }

    private String escapeLikeWildcards(String query) {
        return query.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }

    private Optional<ProductReadContext> toProductReadContext(Product product) {
        return shopRepository.findById(product.getShopId())
                .flatMap(shop -> categoryRepository.findById(product.getCategoryId())
                        .flatMap(category -> identityApi.findUser(shop.getOwnerId())
                                .map(owner -> {
                                    List<ProductImage> images = productImageRepository
                                            .findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId());
                                    boolean ownerActive = owner.accountStatus() == AccountStatus.ACTIVE;
                                    boolean shopApproved = shop.canBePubliclyListed();
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
        if (!shop.canBePubliclyListed()) {
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
