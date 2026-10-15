package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.merchant.api.MerchantStockApi;
import com.senvia.doangiuaky.merchant.api.MerchantStockException;
import com.senvia.doangiuaky.merchant.entity.Product;
import com.senvia.doangiuaky.merchant.entity.ProductStatus;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.StockRestoration;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.repository.StockRestorationRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class MerchantStockService implements MerchantStockApi {

    private final ProductRepository products;
    private final ShopRepository shops;
    private final CategoryRepository categories;
    private final StockRestorationRepository restorations;
    private final IdentityApi identityApi;
    private final EntityManager entityManager;

    public MerchantStockService(ProductRepository products, ShopRepository shops,
                                CategoryRepository categories, StockRestorationRepository restorations,
                                IdentityApi identityApi, EntityManager entityManager) {
        this.products = products;
        this.shops = shops;
        this.categories = categories;
        this.restorations = restorations;
        this.identityApi = identityApi;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deductForCheckout(Long shopId, Long productId, int quantity,
                                  BigDecimal expectedUnitPrice) {
        requirePositive(shopId, "Shop ID");
        requirePositive(productId, "Product ID");
        requirePositiveQuantity(quantity);
        if (expectedUnitPrice == null || expectedUnitPrice.signum() <= 0
                || expectedUnitPrice.scale() > 2) {
            throw new MerchantStockException("Giá sản phẩm không hợp lệ.");
        }

        Product product = requireLockedProduct(shopId, productId);
        Shop shop = shops.findById(shopId)
                .orElseThrow(() -> new MerchantStockException("Gian hàng không tồn tại."));
        boolean ownerActive = identityApi.findUser(shop.getOwnerId())
                .map(owner -> owner.accountStatus() == AccountStatus.ACTIVE)
                .orElse(false);
        boolean categoryActive = categories.findById(product.getCategoryId())
                .map(category -> category.isActive()).orElse(false);
        if (!ownerActive || !shop.canReceiveOrder() || !categoryActive
                || product.getStatus() != ProductStatus.ACTIVE) {
            throw new MerchantStockException("Sản phẩm hiện không thể bán.");
        }
        if (product.getPrice().compareTo(expectedUnitPrice) != 0) {
            throw new MerchantStockException("Giá sản phẩm đã thay đổi.");
        }
        if (products.deductAvailableStock(productId, shopId, quantity,
                expectedUnitPrice, ProductStatus.ACTIVE) != 1) {
            throw new MerchantStockException("Sản phẩm không còn đủ hàng hoặc giá đã thay đổi.");
        }
        // JPQL bulk updates bypass the first-level cache. Refresh only this Merchant entity.
        entityManager.refresh(product);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreCancelledOrderItem(Long orderItemId, Long shopId,
                                          Long productId, int quantity) {
        requirePositive(orderItemId, "Order item ID");
        requirePositive(shopId, "Shop ID");
        requirePositive(productId, "Product ID");
        requirePositiveQuantity(quantity);

        Product product = requireLockedProduct(shopId, productId);
        StockRestoration existing = restorations.findById(orderItemId).orElse(null);
        if (existing != null) {
            if (!existing.matches(shopId, productId, quantity)) {
                throw new MerchantStockException("Dữ liệu hoàn kho không khớp lần trước.");
            }
            return;
        }

        restorations.saveAndFlush(new StockRestoration(orderItemId, shopId, productId, quantity));
        if (products.restoreStock(productId, shopId, quantity) != 1) {
            throw new MerchantStockException("Không thể hoàn kho cho sản phẩm.");
        }
        entityManager.refresh(product);
    }

    private Product requireLockedProduct(Long shopId, Long productId) {
        return products.findByIdForUpdate(productId)
                .filter(product -> product.getShopId().equals(shopId))
                .orElseThrow(() -> new MerchantStockException("Sản phẩm không thuộc gian hàng."));
    }

    private static void requirePositive(Long id, String label) {
        if (id == null || id <= 0) {
            throw new MerchantStockException(label + " không hợp lệ.");
        }
    }

    private static void requirePositiveQuantity(int quantity) {
        if (quantity <= 0) {
            throw new MerchantStockException("Số lượng phải lớn hơn 0.");
        }
    }
}
