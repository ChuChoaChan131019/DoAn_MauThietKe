package com.senvia.doangiuaky.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "products")
public class Product {

    private static final int PRICE_INTEGER_DIGITS = 10;
    private static final int PRICE_FRACTION_DIGITS = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private int stockQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status;

    protected Product() {
    }

    private Product(Long shopId, Long categoryId, String name, String description,
                    BigDecimal price, Integer stockQuantity, ProductStatus status) {
        this.shopId = Objects.requireNonNull(shopId, "Product shop must not be null");
        this.categoryId = Objects.requireNonNull(categoryId, "Product category must not be null");
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
        this.status = Objects.requireNonNull(status, "Product status must not be null");
        applyPriceAndStock(price, stockQuantity);
    }

    /**
     * Creates an active product while enforcing the price and stock invariants.
     * Product CRUD and ownership checks are intentionally left to the application service.
     */
    public static Product create(Long shopId, Long categoryId, String name, String description,
                                 BigDecimal price, Integer stockQuantity) {
        return new Product(shopId, categoryId, name, description, price, stockQuantity, ProductStatus.ACTIVE);
    }

    public void updateDetails(String name, String description) {
        this.name = normalizeName(name);
        this.description = normalizeDescription(description);
    }

    public void changeCategory(Long categoryId) {
        this.categoryId = Objects.requireNonNull(categoryId, "Product category must not be null");
    }

    public void hide() {
        this.status = ProductStatus.HIDDEN;
    }

    public void show() {
        this.status = ProductStatus.ACTIVE;
    }

    /**
     * Changes only the mutable price and stock values after validating both values together.
     * The entity is not changed when either value is invalid.
     */
    public void changePriceAndStock(BigDecimal price, Integer stockQuantity) {
        validatePriceAndStock(price, stockQuantity);
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    private void applyPriceAndStock(BigDecimal price, Integer stockQuantity) {
        validatePriceAndStock(price, stockQuantity);
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    private static void validatePriceAndStock(BigDecimal price, Integer stockQuantity) {
        validatePrice(price);
        if (stockQuantity == null) {
            throw new IllegalArgumentException("Product stock quantity must not be null");
        }
        if (stockQuantity < 0) {
            throw new IllegalArgumentException("Product stock quantity must not be negative");
        }
    }

    private static void validatePrice(BigDecimal price) {
        if (price == null) {
            throw new IllegalArgumentException("Product price must not be null");
        }
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Product price must be greater than zero");
        }
        if (price.scale() > PRICE_FRACTION_DIGITS) {
            throw new IllegalArgumentException("Product price must have at most two decimal places");
        }

        int integerDigits = price.precision() - price.scale();
        if (integerDigits > PRICE_INTEGER_DIGITS) {
            throw new IllegalArgumentException("Product price exceeds NUMERIC(12,2) precision");
        }
    }

    private static String normalizeName(String name) {
        if (name == null || name.trim().isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        String normalized = name.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("Product name must not exceed 200 characters");
        }
        return normalized;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.trim().isBlank()) {
            throw new IllegalArgumentException("Product description must not be blank");
        }
        return description.trim();
    }

    public Long getId() {
        return id;
    }

    public Long getShopId() {
        return shopId;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public int getStockQuantity() {
        return stockQuantity;
    }

    public ProductStatus getStatus() {
        return status;
    }
}
