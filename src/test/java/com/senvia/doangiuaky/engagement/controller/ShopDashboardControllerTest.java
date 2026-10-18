package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsApi;
import com.senvia.doangiuaky.ordering.api.OrderingStatisticsApi;
import com.senvia.doangiuaky.ordering.api.ShopOrderStatistics;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopDashboardControllerTest {

    private final MerchantStatisticsApi merchantStatisticsApi = mock(MerchantStatisticsApi.class);
    private final OrderingStatisticsApi orderingStatisticsApi = mock(OrderingStatisticsApi.class);
    private final ShopDashboardController controller =
            new ShopDashboardController(merchantStatisticsApi, orderingStatisticsApi);

    @Test
    void combinesOwnerShopProductAndOrderStatistics() {
        MerchantShopSummary shop = new MerchantShopSummary(4L, 3L, "Shop", true, true);
        ShopOrderStatistics orderStatistics =
                new ShopOrderStatistics(4L, 2L, Map.of("COMPLETED", 1L), new BigDecimal("125.00"));
        when(merchantStatisticsApi.findCurrentShopSummary()).thenReturn(Optional.of(shop));
        when(merchantStatisticsApi.countProductsForShop(4L)).thenReturn(5L);
        when(orderingStatisticsApi.forOwnShop(4L)).thenReturn(orderStatistics);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("engagement/shop-dashboard", controller.shopDashboard(model));
        assertEquals(shop, model.getAttribute("shop"));
        assertEquals(5L, model.getAttribute("productCount"));
        assertEquals(orderStatistics, model.getAttribute("orderStatistics"));
        verify(merchantStatisticsApi).countProductsForShop(4L);
        verify(orderingStatisticsApi).forOwnShop(4L);
    }

    @Test
    void redirectsWhenTheCurrentUserDoesNotOwnAShop() {
        when(merchantStatisticsApi.findCurrentShopSummary()).thenReturn(Optional.empty());

        assertEquals("redirect:/merchant/shop-status", controller.shopDashboard(new ExtendedModelMap()));
    }
}
