package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.ShopRequestedEvent;
import com.senvia.doangiuaky.merchant.dto.ShopRegistrationForm;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageAsset;
import com.senvia.doangiuaky.merchant.service.image.MerchantImageStorage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

@Service
public class ShopRegistrationService {

    private final ShopRepository shopRepository;
    private final IdentityApi identityApi;
    private final MerchantImageStorage imageStorage;
    private final ApplicationEventPublisher eventPublisher;

    public ShopRegistrationService(ShopRepository shopRepository, IdentityApi identityApi,
                                   MerchantImageStorage imageStorage,
                                   ApplicationEventPublisher eventPublisher) {
        this.shopRepository = shopRepository;
        this.identityApi = identityApi;
        this.imageStorage = imageStorage;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Shop submit(Long ownerId, ShopRegistrationForm form) {
        requireEligibleUser(ownerId);
        if (shopRepository.existsByOwnerId(ownerId)) {
            throw new ShopRegistrationException("Bạn đã có yêu cầu hoặc gian hàng. Không thể gửi thêm yêu cầu mới.");
        }

        MerchantImageAsset logo = imageStorage.uploadShopLogo(ownerId, form.getLogo());
        Runnable cleanupLogo = registerRollbackCleanup(logo.publicId());
        try {
            Shop shop = shopRepository.saveAndFlush(Shop.createPending(
                    ownerId,
                    normalize(form.getShopName()),
                    normalize(form.getDescription()),
                    logo.secureUrl(),
                    logo.publicId(),
                    normalize(form.getPhone()),
                    normalize(form.getAddress())));
            eventPublisher.publishEvent(new ShopRequestedEvent(
                    UUID.randomUUID(),
                    shop.getId(),
                    shop.getOwnerId(),
                    shop.getShopName(),
                    shop.getSubmittedAt()));
            return shop;
        } catch (DataIntegrityViolationException exception) {
            cleanupLogo.run();
            throw new ShopRegistrationException("Bạn đã có yêu cầu hoặc gian hàng. Không thể gửi thêm yêu cầu mới.");
        } catch (RuntimeException exception) {
            cleanupLogo.run();
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public Shop getOwnedShop(Long ownerId) {
        return shopRepository.findByOwnerId(ownerId)
                .orElseThrow(() -> new ShopNotFoundException("Bạn chưa gửi yêu cầu mở gian hàng."));
    }

    private void requireEligibleUser(Long ownerId) {
        if (ownerId == null || ownerId <= 0) {
            throw new ShopRegistrationException("Không xác định được tài khoản người dùng.");
        }
        UserSummary user = identityApi.findUser(ownerId)
                .orElseThrow(() -> new ShopRegistrationException("Không tìm thấy tài khoản người dùng."));
        if (user.role() != UserRole.USER || !identityApi.isUserActive(ownerId)) {
            throw new ShopRegistrationException("Tài khoản hiện không thể gửi yêu cầu mở gian hàng.");
        }
    }

    private Runnable registerRollbackCleanup(String publicId) {
        AtomicBoolean cleanupAttempted = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (cleanupAttempted.compareAndSet(false, true)) {
                deleteUploadedLogo(publicId);
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

    private void deleteUploadedLogo(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            return;
        }
        try {
            imageStorage.delete(publicId);
        } catch (RuntimeException ignored) {
            // The original database error remains the useful error for the caller.
        }
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }
}
