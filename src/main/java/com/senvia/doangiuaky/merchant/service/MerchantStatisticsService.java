package com.senvia.doangiuaky.merchant.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsAccessException;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsApi;
import com.senvia.doangiuaky.merchant.entity.ShopStatus;
import com.senvia.doangiuaky.merchant.repository.ProductRepository;
import com.senvia.doangiuaky.merchant.repository.ShopRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class MerchantStatisticsService implements MerchantStatisticsApi {

    private final CurrentUserApi currentUserApi;
    private final ShopRepository shops;
    private final ProductRepository products;

    public MerchantStatisticsService(CurrentUserApi currentUserApi, ShopRepository shops,
                                     ProductRepository products) {
        this.currentUserApi = currentUserApi;
        this.shops = shops;
        this.products = products;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MerchantShopSummary> findCurrentShopSummary() {
        UserSummary currentUser = currentUserApi.requireCurrentUser();
        if (currentUser.role() != UserRole.USER
                || currentUser.accountStatus() != AccountStatus.ACTIVE
                || currentUser.userId() == null) {
            throw new MerchantStatisticsAccessException("Không có quyền xem thống kê gian hàng.");
        }
        return shops.findByOwnerId(currentUser.userId())
                .map(shop -> new MerchantShopSummary(
                        shop.getId(),
                        shop.getOwnerId(),
                        shop.getShopName(),
                        true,
                        shop.getStatus() == ShopStatus.APPROVED));
    }

    @Override
    @Transactional(readOnly = true)
    public long countProductsForShop(Long shopId) {
        UserSummary currentUser = currentUserApi.requireCurrentUser();
        if (shopId == null || shopId <= 0 || currentUser.role() != UserRole.USER
                || currentUser.accountStatus() != AccountStatus.ACTIVE
                || shops.findById(shopId)
                .filter(shop -> shop.getOwnerId().equals(currentUser.userId())).isEmpty()) {
            throw new MerchantStatisticsAccessException("Không có quyền xem thống kê gian hàng.");
        }
        return products.countByShopId(shopId);
    }
}
