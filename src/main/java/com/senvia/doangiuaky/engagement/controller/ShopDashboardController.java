package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsApi;
import com.senvia.doangiuaky.ordering.api.OrderingStatisticsApi;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
public class ShopDashboardController {

    private final MerchantStatisticsApi merchantStatisticsApi;
    private final OrderingStatisticsApi orderingStatisticsApi;

    public ShopDashboardController(MerchantStatisticsApi merchantStatisticsApi,
                                   OrderingStatisticsApi orderingStatisticsApi) {
        this.merchantStatisticsApi = merchantStatisticsApi;
        this.orderingStatisticsApi = orderingStatisticsApi;
    }

    @GetMapping("/engagement/dashboard")
    public String shopDashboard(Model model) {
        Optional<MerchantShopSummary> currentShop = merchantStatisticsApi.findCurrentShopSummary();
        if (currentShop.isEmpty()) {
            return "redirect:/merchant/shop-status";
        }
        MerchantShopSummary shop = currentShop.orElseThrow();

        model.addAttribute("shop", shop);
        model.addAttribute("productCount", merchantStatisticsApi.countProductsForShop(shop.shopId()));
        model.addAttribute("orderStatistics", orderingStatisticsApi.forOwnShop(shop.shopId()));
        return "engagement/shop-dashboard";
    }
}
