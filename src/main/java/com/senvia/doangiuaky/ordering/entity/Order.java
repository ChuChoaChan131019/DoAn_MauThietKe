package com.senvia.doangiuaky.ordering.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "order_code",
        nullable = false,
        unique = true,
        length = 30
    )
    private String orderCode;

    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;

    @Column(name = "shop_id", nullable = false)
    private Long shopId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(
        name = "receiver_name",
        nullable = false,
        length = 100
    )
    private String receiverName;

    @Column(
        name = "receiver_phone",
        nullable = false,
        length = 20
    )
    private String receiverPhone;

    @Column(
        name = "shipping_address",
        nullable = false,
        columnDefinition = "TEXT"
    )
    private String shippingAddress;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(
        name = "total_amount",
        nullable = false,
        precision = 14,
        scale = 2
    )
    private BigDecimal totalAmount;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Order() {
    }

    public Order(String orderCode, Long buyerId, Long shopId, String receiverName, String receiverPhone,
            String shippingAddress, String note, BigDecimal totalAmount) {
        this.orderCode = Objects.requireNonNull(orderCode);
        this.buyerId = Objects.requireNonNull(buyerId);
        this.shopId = Objects.requireNonNull(shopId);
        this.receiverName = Objects.requireNonNull(receiverName);
        this.receiverPhone = Objects.requireNonNull(receiverPhone);
        this.shippingAddress = Objects.requireNonNull(shippingAddress);
        this.note = note;
        this.totalAmount = Objects.requireNonNull(totalAmount);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public Long getBuyerId() {
        return buyerId;
    }

    public Long getShopId() {
        return shopId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public String getNote() {
        return note;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
