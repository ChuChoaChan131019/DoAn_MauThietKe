package com.senvia.doangiuaky.merchant.entity;

import com.senvia.doangiuaky.merchant.state.ShopState;
import com.senvia.doangiuaky.merchant.state.ShopStateFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "shops")
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_id", nullable = false, unique = true)
    private Long ownerId;

    @Column(name = "shop_name", nullable = false, length = 150)
    private String shopName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Column(name = "logo_public_id", length = 255)
    private String logoPublicId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShopStatus status = ShopStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "approved_by")
    private Long approvedById;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "lock_reason", length = 500)
    private String lockReason;

    @Column(name = "locked_by")
    private Long lockedById;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Shop() {
    }

    private Shop(Long ownerId, String shopName, String description, String logoUrl,
                 String logoPublicId, String phone, String address) {
        this.ownerId = Objects.requireNonNull(ownerId, "Shop owner must not be null");
        this.shopName = Objects.requireNonNull(shopName, "Shop name must not be null");
        this.description = description;
        this.logoUrl = logoUrl;
        this.logoPublicId = logoPublicId;
        this.phone = Objects.requireNonNull(phone, "Shop phone must not be null");
        this.address = Objects.requireNonNull(address, "Shop address must not be null");
        this.status = ShopStatus.PENDING;
    }

    public static Shop createPending(Long ownerId, String shopName, String description, String logoUrl,
                                     String logoPublicId, String phone, String address) {
        return new Shop(ownerId, shopName, description, logoUrl, logoPublicId, phone, address);
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        submittedAt = now;
        createdAt = now;
        updatedAt = now;
        status = ShopStatus.PENDING;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getShopName() { return shopName; }
    public String getDescription() { return description; }
    public String getLogoUrl() { return logoUrl; }
    public String getLogoPublicId() { return logoPublicId; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public ShopStatus getStatus() { return status; }
    public Instant getSubmittedAt() { return submittedAt; }
    public String getRejectionReason() { return rejectionReason; }
    public String getLockReason() { return lockReason; }
    public Long getLockedById() { return lockedById; }
    public Instant getLockedAt() { return lockedAt; }
    public Long getApprovedById() { return approvedById; }
    public Instant getApprovedAt() { return approvedAt; }

    public void approve(Long adminId, Instant approvedAt) {
        Objects.requireNonNull(adminId, "Approving admin must not be null");
        Objects.requireNonNull(approvedAt, "Approval time must not be null");

        status = state().approve();
        approvedById = adminId;
        this.approvedAt = approvedAt;
        rejectionReason = null;
    }

    public void reject(Long adminId, String reason) {
        Objects.requireNonNull(adminId, "Rejecting admin must not be null");
        String normalizedReason = normalizeRejectionReason(reason);

        status = state().reject();
        approvedById = adminId;
        approvedAt = null;
        rejectionReason = normalizedReason;
    }

    public void resubmit(String shopName, String description, String logoUrl,
                         String logoPublicId, String phone, String address,
                         Instant submittedAt) {
        Objects.requireNonNull(shopName, "Shop name must not be null");
        Objects.requireNonNull(phone, "Shop phone must not be null");
        Objects.requireNonNull(address, "Shop address must not be null");
        Objects.requireNonNull(submittedAt, "Submission time must not be null");

        status = state().resubmit();
        this.shopName = shopName;
        this.description = description;
        this.logoUrl = logoUrl;
        this.logoPublicId = logoPublicId;
        this.phone = phone;
        this.address = address;
        this.submittedAt = submittedAt;
        rejectionReason = null;
        approvedById = null;
        approvedAt = null;
    }

    public void updateApprovedDetails(String shopName, String description, String logoUrl,
                                      String logoPublicId, String phone, String address) {
        if (status != ShopStatus.APPROVED) {
            throw new IllegalStateException("Only an approved shop can update details");
        }
        this.shopName = Objects.requireNonNull(shopName, "Shop name must not be null");
        this.description = description;
        this.logoUrl = logoUrl;
        this.logoPublicId = logoPublicId;
        this.phone = Objects.requireNonNull(phone, "Shop phone must not be null");
        this.address = Objects.requireNonNull(address, "Shop address must not be null");
    }

    public void lock(Long adminId, String reason, Instant lockedAt) {
        Objects.requireNonNull(adminId, "Locking admin must not be null");
        Objects.requireNonNull(lockedAt, "Lock time must not be null");
        if (adminId <= 0) {
            throw new IllegalArgumentException("Locking admin must be positive");
        }
        String normalizedReason = normalizeLockReason(reason);

        status = state().lock();
        lockReason = normalizedReason;
        lockedById = adminId;
        this.lockedAt = lockedAt;
    }

    public void unlock() {
        status = state().unlock();
        lockReason = null;
        lockedById = null;
        lockedAt = null;
    }

    public boolean canAddProduct() { return state().canAddProduct(); }
    public boolean canReceiveOrder() { return state().canReceiveOrder(); }
    public boolean canBePubliclyListed() { return state().canBePubliclyListed(); }
    public boolean canResubmit() { return state().canResubmit(); }
    public boolean canHandleExistingOrders() { return state().canHandleExistingOrders(); }

    private ShopState state() {
        return ShopStateFactory.resolve(status);
    }

    private static String normalizeRejectionReason(String reason) {
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Shop rejection reason must not be blank");
        }
        return reason.trim();
    }

    private static String normalizeLockReason(String reason) {
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Shop lock reason must not be blank");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Shop lock reason must not exceed 500 characters");
        }
        return normalized;
    }
}
