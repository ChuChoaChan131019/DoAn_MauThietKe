package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.IdentityApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.dto.AdminShopDetail;
import com.senvia.doangiuaky.merchant.dto.AdminShopSummary;
import com.senvia.doangiuaky.merchant.entity.Shop;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AdminShopManagementService {

    private static final Set<ShopStatus> MANAGED_STATUSES =
            Set.of(ShopStatus.APPROVED, ShopStatus.LOCKED);

    private final ShopRepository shopRepository;
    private final IdentityApi identityApi;

    public AdminShopManagementService(ShopRepository shopRepository, IdentityApi identityApi) {
        this.shopRepository = shopRepository;
        this.identityApi = identityApi;
    }

    public List<AdminShopSummary> listManagedShops(Long adminId) {
        requireActiveAdmin(adminId);
        return shopRepository.findAllByStatusInOrderByShopNameAscIdAsc(MANAGED_STATUSES).stream()
                .map(this::toSummary)
                .toList();
    }

    public AdminShopDetail getManagedShop(Long adminId, Long shopId) {
        requireActiveAdmin(adminId);
        if (shopId == null || shopId <= 0) {
            throw notFound();
        }
        Shop shop = shopRepository.findByIdAndStatusIn(shopId, MANAGED_STATUSES)
                .orElseThrow(AdminShopManagementService::notFound);
        return toDetail(shop);
    }

    private AdminShopSummary toSummary(Shop shop) {
        return new AdminShopSummary(
                shop.getId(),
                shop.getShopName(),
                requireUserName(shop.getOwnerId(), "Không tìm thấy chủ gian hàng."),
                shop.getStatus(),
                shop.getApprovedAt(),
                shop.getLockedAt());
    }

    private AdminShopDetail toDetail(Shop shop) {
        return new AdminShopDetail(
                shop.getId(),
                shop.getShopName(),
                shop.getDescription(),
                shop.getLogoUrl(),
                shop.getPhone(),
                shop.getAddress(),
                requireUserName(shop.getOwnerId(), "Không tìm thấy chủ gian hàng."),
                shop.getStatus(),
                optionalUserName(shop.getApprovedById()),
                shop.getApprovedAt(),
                shop.getLockReason(),
                optionalUserName(shop.getLockedById()),
                shop.getLockedAt());
    }

    private void requireActiveAdmin(Long adminId) {
        if (adminId == null || adminId <= 0 || identityApi.findUser(adminId)
                .filter(user -> user.role() == UserRole.ADMIN
                        && user.accountStatus() == AccountStatus.ACTIVE)
                .isEmpty()) {
            throw new ShopLockAuthorizationException(
                    "Chỉ quản trị viên đang hoạt động mới được quản lý gian hàng.");
        }
    }

    private String requireUserName(Long userId, String message) {
        return identityApi.findUser(userId)
                .map(UserSummary::fullName)
                .orElseThrow(() -> new ShopManagementNotFoundException(message));
    }

    private String optionalUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        return identityApi.findUser(userId).map(UserSummary::fullName).orElse("Không xác định");
    }

    private static ShopManagementNotFoundException notFound() {
        return new ShopManagementNotFoundException("Không tìm thấy gian hàng cần quản lý.");
    }
}
