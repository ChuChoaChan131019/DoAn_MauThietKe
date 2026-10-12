package com.senvia.doangiuaky.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "image_url", nullable = false, columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "public_id", nullable = false, length = 255)
    private String publicId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProductImage() {
    }

    private ProductImage(Product product, String imageUrl, String publicId, int displayOrder) {
        this.product = Objects.requireNonNull(product, "Product image product must not be null");
        this.imageUrl = requireText(imageUrl, "Product image URL must not be blank");
        this.publicId = requireText(publicId, "Product image public ID must not be blank");
        if (displayOrder < 0) {
            throw new IllegalArgumentException("Product image display order must not be negative");
        }
        this.displayOrder = displayOrder;
    }

    public static ProductImage create(Product product, String imageUrl, String publicId, int displayOrder) {
        return new ProductImage(product, imageUrl, publicId, displayOrder);
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getPublicId() {
        return publicId;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
