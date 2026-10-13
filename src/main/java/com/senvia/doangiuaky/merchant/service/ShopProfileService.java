package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.merchant.dto.ShopUpdateForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Objects;

@Service
public class ShopProfileService {

    private final ShopRepository shopRepository;
    private final IdentityApi identityApi;
    private final MerchantImageStorage imageStorage;

    public ShopProfileService(ShopRepository shopRepository,
                              IdentityApi identityApi,
                              MerchantImageStorage imageStorage) {
        this.shopRepository = shopRepository;
        this.identityApi = identityApi;
        this.imageStorage = imageStorage;
    }

    @Transactional(readOnly = true)
    public Shop getOwnedApprovedShop(Long ownerId) {
        requireActiveOwner(ownerId);
        Shop shop = shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ShopNotFoundException(
                        "Bạn chưa có gian hàng."));
        requireApprovedShop(shop);
        return shop;
    }

    @Transactional
    public Shop update(Long ownerId, ShopUpdateForm form) {
        requireActiveOwner(ownerId);
        Shop shop = shopRepository.findByOwnerIdForUpdate(ownerId)
                .orElseThrow(() -> new ShopNotFoundException("Bạn chưa có gian hàng."));
        requireApprovedShop(shop);

        boolean hasNewLogo = form.getLogo() != null && !form.getLogo().isEmpty();
        boolean removeLogo = form.isRemoveLogo() && !hasNewLogo;
        String previousLogoPublicId = shop.getLogoPublicId();
        MerchantImageAsset replacementLogo = null;
        Runnable rollbackCleanup = () -> { };

        if (hasNewLogo) {
            replacementLogo = imageStorage.uploadShopLogo(ownerId, form.getLogo());
            if (replacementLogo == null || !StringUtils.hasText(replacementLogo.secureUrl())
                    || !StringUtils.hasText(replacementLogo.publicId())) {
                if (replacementLogo != null && StringUtils.hasText(replacementLogo.publicId())) {
                    deleteImage(replacementLogo.publicId());
                }
                throw new ShopProfileException("Cloudinary không trả về thông tin logo hợp lệ.");
            }
            rollbackCleanup = registerRollbackCleanup(replacementLogo.publicId());
        }

        try {
            shop.updateApprovedDetails(
                    normalizeRequired(form.getShopName(), "Vui lòng nhập tên gian hàng."),
                    normalizeRequired(form.getDescription(), "Vui lòng nhập mô tả gian hàng."),
                    hasNewLogo ? replacementLogo.secureUrl() : removeLogo ? null : shop.getLogoUrl(),
                    hasNewLogo ? replacementLogo.publicId() : removeLogo ? null : shop.getLogoPublicId(),
                    normalizeRequired(form.getPhone(), "Vui lòng nhập số điện thoại hỗ trợ."),
                    normalizeRequired(form.getAddress(), "Vui lòng nhập địa chỉ cửa hàng hoặc kho hàng."));
            Shop savedShop = shopRepository.saveAndFlush(shop);
            registerAfterCommitCleanup(previousLogoPublicId, replacementLogo, removeLogo);
            return savedShop;
        } catch (RuntimeException exception) {
            rollbackCleanup.run();
            throw exception;
        }
    }

    private void requireActiveOwner(Long ownerId) {
        if (ownerId == null || ownerId <= 0
                || identityApi.findUser(ownerId)
                .filter(user -> user.role() == UserRole.USER
                        && user.accountStatus() == AccountStatus.ACTIVE)
                .isEmpty()) {
            throw new ShopProfileAuthorizationException(
                    "Chỉ chủ shop đang hoạt động mới được cập nhật thông tin gian hàng.");
        }
    }

    private static void requireApprovedShop(Shop shop) {
        if (shop.getStatus() != ShopStatus.APPROVED) {
            throw new ShopProfileAuthorizationException(
                    "Chỉ gian hàng đã được duyệt mới được cập nhật thông tin.");
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
                                             MerchantImageAsset replacementLogo,
                                             boolean removeLogo) {
        if (!StringUtils.hasText(previousLogoPublicId)
                || (!removeLogo && replacementLogo == null)
                || (replacementLogo != null
                && Objects.equals(previousLogoPublicId, replacementLogo.publicId()))) {
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
        if (!StringUtils.hasText(publicId)) {
            return;
        }
        try {
            imageStorage.delete(publicId);
        } catch (RuntimeException ignored) {
            // A cleanup failure must not hide the completed database operation.
        }
    }

    private static String normalizeRequired(String value, String message) {
        if (value == null || value.trim().isBlank()) {
            throw new ShopProfileException(message);
        }
        return value.trim();
    }
}
