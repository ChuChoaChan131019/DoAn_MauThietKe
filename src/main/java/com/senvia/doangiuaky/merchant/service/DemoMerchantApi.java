package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.CartProductView;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.ShopOrderCapabilities;
import com.senvia.doangiuaky.merchant.dto.CatalogProductView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.entity.Product;
import com.senvia.doangiuaky.merchant.entity.ProductStatus;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductImageRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Database-backed Merchant adapter used by Shopping and the current catalog UI.
 * The metadata map only supplies presentation assets that are not yet stored in
 * the catalog schema; identity, price, stock and saleability come from the database.
 */
@Service
public class DemoMerchantApi implements MerchantApi {

    private static final String FALLBACK_IMAGE =
            "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=600&q=80";

    private static final Map<String, PresentationMetadata> PRESENTATION = Map.of(
            "Canvas Tote Bag", new PresentationMetadata(
                    "https://images.unsplash.com/photo-1544816155-12df9643f363?w=600&q=80", true, 0, null, 4.5, 24),
            "Linen Shirt", new PresentationMetadata(
                    "https://images.unsplash.com/photo-1598033129183-c4f50c736f10?w=600&q=80", false, 15,
                    new BigDecimal("1050000"), 0.0, 0),
            "Brass Table Lamp", new PresentationMetadata(
                    "https://images.unsplash.com/photo-1513506003901-1e6a35d10d3f?w=600&q=80", false, 0, null, 5.0, 2),
            "Signature Parfum", new PresentationMetadata(
                    "https://images.unsplash.com/photo-1541643600914-78b084683702?w=600&q=80", true, 0, null, 4.8, 120),
            "Ceramic Vase", new PresentationMetadata(
                    "https://images.unsplash.com/photo-1612196808214-b8e1d6145a8c?w=600&q=80", false, 0, null, 4.0, 5),
            "Wall Frame", new PresentationMetadata(
                    "https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=600&q=80", false, 0, null, 4.5, 10),
            "Leather Wallet", new PresentationMetadata(
                    "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&q=80", false, 0, null, 4.9, 50),
            "Desk Organizer", new PresentationMetadata(
                    "https://images.unsplash.com/photo-1588345921523-c2dcdb7f1dcd?w=600&q=80", false, 0, null, 4.7, 18));

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
        return productRepository.findById(productId).flatMap(this::toCartProductView);
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
    public List<CatalogProductView> findSaleableCatalogProducts() {
        return productRepository.findAllByOrderByIdAsc().stream()
                .map(product -> toCartProductView(product)
                        .filter(CartProductView::isSaleable)
                        .flatMap(ignored -> toCatalogProductView(product)))
                .flatMap(Optional::stream)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<CatalogProductView> findSaleableCatalogProduct(Long productId) {
        if (productId == null || productId <= 0) {
            return Optional.empty();
        }
        return productRepository.findById(productId)
                .filter(product -> toCartProductView(product)
                        .map(CartProductView::isSaleable)
                        .orElse(false))
                .flatMap(this::toCatalogProductView);
    }

    private Optional<CartProductView> toCartProductView(Product product) {
        Optional<Shop> shop = shopRepository.findById(product.getShopId());
        Optional<Category> category = categoryRepository.findById(product.getCategoryId());
        if (shop.isEmpty() || category.isEmpty()) {
            return Optional.empty();
        }

        Shop productShop = shop.get();
        Optional<UserSummary> owner = identityApi.findUser(productShop.getOwnerId());
        if (owner.isEmpty()) {
            return Optional.empty();
        }

        PresentationMetadata metadata = metadataFor(product.getName());
        return Optional.of(new CartProductView(
                product.getId(),
                productShop.getId(),
                productShop.getOwnerId(),
                productShop.getShopName(),
                product.getName(),
                imageUrl(product, metadata.image()),
                product.getPrice(),
                product.getStockQuantity(),
                owner.get().accountStatus() == AccountStatus.ACTIVE,
                productShop.getStatus() == ShopStatus.APPROVED,
                category.get().isActive(),
                product.getStatus() == ProductStatus.ACTIVE));
    }

    private Optional<CatalogProductView> toCatalogProductView(Product product) {
        Optional<Shop> shop = shopRepository.findById(product.getShopId());
        Optional<Category> category = categoryRepository.findById(product.getCategoryId());
        if (shop.isEmpty() || category.isEmpty()) {
            return Optional.empty();
        }

        PresentationMetadata metadata = metadataFor(product.getName());
        return Optional.of(new CatalogProductView(
                product.getId(),
                product.getName(),
                brandName(shop.get().getShopName()),
                product.getPrice(),
                metadata.isNew(),
                metadata.discount(),
                metadata.oldPrice(),
                imageUrl(product, metadata.image()),
                product.getDescription(),
                product.getStockQuantity(),
                metadata.rating(),
                metadata.reviewCount(),
                category.get().getName(),
                List.of()));
    }

    private static PresentationMetadata metadataFor(String productName) {
        return PRESENTATION.getOrDefault(productName,
                new PresentationMetadata(FALLBACK_IMAGE, false, 0, null, 0.0, 0));
    }

    private String imageUrl(Product product, String fallback) {
        return productImageRepository.findAllByProductIdOrderByDisplayOrderAscIdAsc(product.getId())
                .stream()
                .findFirst()
                .map(image -> image.getImageUrl())
                .orElse(fallback);
    }

    private static String brandName(String shopName) {
        return shopName != null && shopName.endsWith(" Store")
                ? shopName.substring(0, shopName.length() - " Store".length())
                : shopName;
    }

    private record PresentationMetadata(
            String image,
            boolean isNew,
            int discount,
            BigDecimal oldPrice,
            double rating,
            int reviewCount) {
    }
}
