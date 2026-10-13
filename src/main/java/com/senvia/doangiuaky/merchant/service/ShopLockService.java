package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class ShopLockService {

    private final ShopRepository shopRepository;
    private final IdentityApi identityApi;

    public ShopLockService(ShopRepository shopRepository, IdentityApi identityApi) {
        this.shopRepository = shopRepository;
        this.identityApi = identityApi;
    }

    @Transactional
    public void lock(Long shopId, Long adminId, String reason) {
        String normalizedReason = normalizeReason(reason);
        requireActiveAdmin(adminId);
        Shop shop = findShopForUpdate(shopId);

        shop.lock(adminId, normalizedReason, Instant.now());
        shopRepository.saveAndFlush(shop);
    }

    @Transactional
    public void unlock(Long shopId, Long adminId) {
        requireActiveAdmin(adminId);
        Shop shop = findShopForUpdate(shopId);

        shop.unlock();
        shopRepository.saveAndFlush(shop);
    }

    private void requireActiveAdmin(Long adminId) {
        if (adminId == null || adminId <= 0 || identityApi.findUser(adminId)
                .filter(user -> user.role() == UserRole.ADMIN
                        && user.accountStatus() == AccountStatus.ACTIVE)
                .isEmpty()) {
            throw new ShopLockAuthorizationException(
                    "Chỉ quản trị viên đang hoạt động mới được khóa hoặc mở khóa gian hàng.");
        }
    }

    private Shop findShopForUpdate(Long shopId) {
        if (shopId == null || shopId <= 0) {
            throw notFound();
        }
        return shopRepository.findByIdForUpdate(shopId).orElseThrow(ShopLockService::notFound);
    }

    private static String normalizeReason(String reason) {
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Shop lock reason must not be blank");
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("Shop lock reason must not exceed 500 characters");
        }
        return normalized;
    }

    private static ShopManagementNotFoundException notFound() {
        return new ShopManagementNotFoundException("Không tìm thấy gian hàng cần quản lý.");
    }
}
