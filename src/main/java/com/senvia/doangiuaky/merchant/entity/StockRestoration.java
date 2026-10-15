package com.senvia.doangiuaky.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "stock_restorations")
public class StockRestoration {

    @Id
    @Column(name = "order_item_id")
    private Long orderItemId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockRestoration() {
    }

    public StockRestoration(Long orderItemId, Long shopId, Long productId, int quantity) {
        this.orderItemId = orderItemId;
        this.shopId = shopId;
        this.productId = productId;
        this.quantity = quantity;
        this.createdAt = Instant.now();
    }

    public boolean matches(Long shopId, Long productId, int quantity) {
        return this.shopId.equals(shopId) && this.productId.equals(productId)
                && this.quantity == quantity;
    }
}
