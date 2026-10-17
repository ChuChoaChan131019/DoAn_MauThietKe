package com.senvia.doangiuaky.ordering.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
        name = "order_id",
        nullable = false,
        unique = true
    )
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method;

    @Column(
        nullable = false,
        precision = 14,
        scale = 2
    )
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @Column(
        name = "transaction_reference",
        length = 100
    )
    private String transactionReference;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    @Column(
        name = "created_at",
        nullable = false,
        updatable = false
    )
    private Instant createdAt;

    protected Payment() {
    }

    public Payment(
            Long orderId,
            PaymentMethod method,
            BigDecimal amount,
            PaymentStatus status,
            String transactionReference
    ) {
        this.orderId = Objects.requireNonNull(orderId);
        this.method = Objects.requireNonNull(method);
        this.amount = Objects.requireNonNull(amount);
        this.status = Objects.requireNonNull(status);
        this.transactionReference = transactionReference;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrderId() {
        return orderId;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // PAY 05: Xác nhận đã nhận tiền chuyển khoản mô phỏng.
    public void markBankTransferPaid() {
        if (method != PaymentMethod.BANK_TRANSFER || status != PaymentStatus.PENDING || paidAt != null) {
            throw new IllegalStateException("Thanh toán không ở trạng thái chờ chuyển khoản.");
        }

        status = PaymentStatus.PAID;
        paidAt = Instant.now();
    }
}