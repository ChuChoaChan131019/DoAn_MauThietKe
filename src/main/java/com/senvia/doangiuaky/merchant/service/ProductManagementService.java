package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.merchant.dto.ProductForm;
import com.senvia.doangiuaky.merchant.dto.SellerProductView;
import com.senvia.doangiuaky.merchant.entity.Category;
import com.senvia.doangiuaky.merchant.entity.Product;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.repository.CategoryRepository;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductManagementService {

    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryManagementService categoryService;
    private final IdentityApi identityApi;

    public ProductManagementService(ProductRepository productRepository,
                                    ShopRepository shopRepository,
                                    CategoryRepository categoryRepository,
                                    CategoryManagementService categoryService,
                                    IdentityApi identityApi) {
        this.productRepository = productRepository;
        this.shopRepository = shopRepository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
        this.identityApi = identityApi;
    }

    @Transactional(readOnly = true)
    public List<SellerProductView> listOwnedProducts(Long ownerId) {
        Shop shop = requireApprovedShop(ownerId);
        return productRepository.findAllByShopIdOrderByIdAsc(shop.getId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public SellerProductView getOwnedProduct(Long ownerId, Long productId) {
        Shop shop = requireApprovedShop(ownerId);
        return toView(findOwnedProduct(productId, shop.getId()));
    }

    @Transactional
    public SellerProductView create(Long ownerId, ProductForm form) {
        Shop shop = requireApprovedShop(ownerId);
        Category category = categoryService.requireActiveCategory(form.getCategoryId());
        Product product = Product.create(
                shop.getId(),
                category.getId(),
                form.getName(),
                form.getDescription(),
                form.getPrice(),
                form.getStockQuantity());
        return toView(productRepository.saveAndFlush(product));
    }

    @Transactional
    public SellerProductView update(Long ownerId, Long productId, ProductForm form) {
        Shop shop = requireApprovedShop(ownerId);
        Product product = findOwnedProduct(productId, shop.getId());
        Category category = categoryService.requireActiveCategory(form.getCategoryId());

        product.updateDetails(form.getName(), form.getDescription());
        product.changeCategory(category.getId());
        product.changePriceAndStock(form.getPrice(), form.getStockQuantity());
        return toView(productRepository.saveAndFlush(product));
    }

    @Transactional
    public void hide(Long ownerId, Long productId) {
        Shop shop = requireApprovedShop(ownerId);
        Product product = findOwnedProduct(productId, shop.getId());
        product.hide();
        productRepository.saveAndFlush(product);
    }

    private Shop requireApprovedShop(Long ownerId) {
        if (ownerId == null || identityApi.findUser(ownerId)
                .filter(user -> user.role() == UserRole.USER
                        && user.accountStatus() == AccountStatus.ACTIVE)
                .isEmpty()) {
            throw new ProductAuthorizationException(
                    "Chỉ tài khoản người bán đang hoạt động mới được quản lý sản phẩm.");
        }

        Shop shop = shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ProductAuthorizationException("Bạn chưa có gian hàng."));
        if (!shop.canAddProduct()) {
            throw new ProductAuthorizationException(
                    "Chỉ gian hàng đã được duyệt mới được quản lý sản phẩm.");
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

    private SellerProductView toView(Product product) {
        Category category = categoryRepository.findById(product.getCategoryId()).orElse(null);
        String categoryName = category == null ? "Không xác định" : category.getName();
        return new SellerProductView(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getCategoryId(),
                categoryName,
                category != null && category.isActive(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getStatus());
    }
}
