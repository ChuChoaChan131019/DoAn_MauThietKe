package com.senvia.doangiuaky.ordering.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(
        name = "product_name_snapshot",
        nullable = false,
        length = 200
    )
    private String productNameSnapshot;

    @Column(
        name = "unit_price",
        nullable = false,
        precision = 12,
        scale = 2
    )
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(
        nullable = false,
        precision = 14,
        scale = 2
    )
    private BigDecimal subtotal;

    protected OrderItem() {
    }

    public OrderItem(
            Long orderId,
            Long productId,
            String productNameSnapshot,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal
    ) {
        this.orderId = Objects.requireNonNull(orderId);
        this.productId = Objects.requireNonNull(productId);
        this.productNameSnapshot =
                Objects.requireNonNull(productNameSnapshot);
        this.unitPrice = Objects.requireNonNull(unitPrice);
        this.quantity = quantity;
        this.subtotal = Objects.requireNonNull(subtotal);
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getProductNameSnapshot() {
        return productNameSnapshot;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }
}