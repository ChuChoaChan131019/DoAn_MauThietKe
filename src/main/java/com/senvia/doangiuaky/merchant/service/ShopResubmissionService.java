package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.ShopRequestedEvent;
import com.senvia.doangiuaky.merchant.dto.ShopResubmissionForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorageException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ShopResubmissionService {

    private final ShopRepository shopRepository;
    private final IdentityApi identityApi;
    private final MerchantImageStorage imageStorage;
    private final ApplicationEventPublisher eventPublisher;

    public ShopResubmissionService(ShopRepository shopRepository,
                                   IdentityApi identityApi,
                                   MerchantImageStorage imageStorage,
                                   ApplicationEventPublisher eventPublisher) {
        this.shopRepository = shopRepository;
        this.identityApi = identityApi;
        this.imageStorage = imageStorage;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Shop getRejectedShop(Long ownerId) {
        requireActiveOwner(ownerId);
        Shop shop = shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ShopNotFoundException(
                        "Bạn chưa gửi yêu cầu mở gian hàng."));
        requireResubmittable(shop);
        return shop;
    }

    @Transactional
    public Shop resubmit(Long ownerId, ShopResubmissionForm form) {
        requireActiveOwner(ownerId);
        Objects.requireNonNull(form, "Shop resubmission form must not be null");

        Shop shop = shopRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new ShopNotFoundException(
                        "Bạn chưa gửi yêu cầu mở gian hàng."));
        requireResubmittable(shop);

        MerchantImageAsset replacementLogo = null;
        Runnable rollbackCleanup = () -> { };
        String previousLogoPublicId = shop.getLogoPublicId();

        if (form.getLogo() != null && !form.getLogo().isEmpty()) {
            replacementLogo = imageStorage.uploadShopLogo(ownerId, form.getLogo());
            if (replacementLogo == null || !StringUtils.hasText(replacementLogo.secureUrl())
                    || !StringUtils.hasText(replacementLogo.publicId())) {
                if (replacementLogo != null && StringUtils.hasText(replacementLogo.publicId())) {
                    deleteImage(replacementLogo.publicId());
                }
                throw new MerchantImageStorageException("Không nhận được thông tin logo hợp lệ.");
            }
            rollbackCleanup = registerRollbackCleanup(replacementLogo.publicId());
        }

        try {
            Instant submittedAt = Instant.now();
            shop.resubmit(
                    normalizeRequired(form.getShopName(), "Vui lòng nhập tên gian hàng."),
                    normalizeRequired(form.getDescription(), "Vui lòng nhập mô tả gian hàng."),
                    replacementLogo == null ? shop.getLogoUrl() : replacementLogo.secureUrl(),
                    replacementLogo == null ? shop.getLogoPublicId() : replacementLogo.publicId(),
                    normalizeRequired(form.getPhone(), "Vui lòng nhập số điện thoại hỗ trợ."),
                    normalizeRequired(form.getAddress(), "Vui lòng nhập địa chỉ cửa hàng hoặc kho hàng."),
                    submittedAt);

            Shop savedShop = shopRepository.saveAndFlush(shop);
            eventPublisher.publishEvent(new ShopRequestedEvent(
                    UUID.randomUUID(),
                    savedShop.getId(),
                    savedShop.getOwnerId(),
                    savedShop.getShopName(),
                    savedShop.getSubmittedAt()));
            registerAfterCommitCleanup(previousLogoPublicId, replacementLogo);
            return savedShop;
        } catch (RuntimeException exception) {
            rollbackCleanup.run();
            throw exception;
        }
    }

    private void requireActiveOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new ShopResubmissionAuthorizationException(
                    "Không xác định được tài khoản chủ gian hàng.");
        }
        identityApi.findUser(ownerId)
                .filter(candidate -> candidate.role() == UserRole.USER
                        && candidate.accountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ShopResubmissionAuthorizationException(
                        "Tài khoản hiện không thể gửi lại yêu cầu gian hàng."));
    }

    private static void requireResubmittable(Shop shop) {
        if (!shop.canResubmit()) {
            throw new ShopResubmissionException(
                    "Chỉ yêu cầu gian hàng bị từ chối mới được sửa và gửi lại.");
        }
    }

    private Runnable registerRollbackCleanup(String publicId) {
        AtomicBoolean cleanupAttempted = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (cleanupAttempted.compareAndSet(false, true)) {
                deleteImage(publicId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != TransactionSynchronization.STATUS_COMMITTED) {
                        cleanup.run();
                    }
                }
            });
        }
        return cleanup;
    }

    private void registerAfterCommitCleanup(String previousLogoPublicId,
                                             MerchantImageAsset replacementLogo) {
        if (!StringUtils.hasText(previousLogoPublicId)
                || replacementLogo == null
                || Objects.equals(previousLogoPublicId, replacementLogo.publicId())) {
            return;
        }
        Runnable cleanup = () -> deleteImage(previousLogoPublicId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == TransactionSynchronization.STATUS_COMMITTED) {
                        cleanup.run();
                    }
                }
            });
        } else {
            cleanup.run();
        }
    }

    private void deleteImage(String publicId) {
        try {
            imageStorage.delete(publicId);
        } catch (RuntimeException ignored) {
            // A cleanup failure must not hide the completed shop transaction.
        }
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new ShopResubmissionException(message);
        }
        return value.trim();
    }
}
