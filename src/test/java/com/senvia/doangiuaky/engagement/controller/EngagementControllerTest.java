package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.NotificationListItem;
import com.senvia.doangiuaky.engagement.dto.NotificationPageView;
import com.senvia.doangiuaky.engagement.entity.NotificationType;
import com.senvia.doangiuaky.engagement.service.NotificationNotFoundException;
import com.senvia.doangiuaky.engagement.service.NotificationService;
import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsAccessException;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsApi;
import com.senvia.doangiuaky.merchant.service.DemoMerchantApi;
import com.senvia.doangiuaky.ordering.api.ShopOrderStatistics;
import com.senvia.doangiuaky.ordering.service.OrderingContractService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class EngagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private MerchantStatisticsApi merchantStatisticsApi;

    @MockitoBean
    private CurrentUserApi currentUserApi;

    @MockitoBean
    private DemoMerchantApi merchantApi;

    @MockitoBean
    private OrderingContractService orderingStatisticsApi;

    @Test
    void anonymousListRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/account/notifications"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shopOwnerCanOpenStatisticsDashboard() throws Exception {
        when(merchantStatisticsApi.countProductsForCurrentShop()).thenReturn(12L);
        when(currentUserApi.requireCurrentUser())
                .thenReturn(new UserSummary(7L, "Shop owner", UserRole.USER, AccountStatus.ACTIVE));
        when(merchantApi.findShopSummaryForOwner(7L))
                .thenReturn(Optional.of(new MerchantShopSummary(11L, 7L, "Shop", true, true)));
        Map<String, Long> statusCounts = Map.of(
                "PENDING", 2L, "CONFIRMED", 1L, "PREPARING", 1L,
                "SHIPPING", 1L, "COMPLETED", 3L, "CANCELLED", 1L);
        BigDecimal completedRevenue = new BigDecimal("1234567.89");
        ShopOrderStatistics stats = new ShopOrderStatistics(11L, 9L, statusCounts, completedRevenue);
        assertEquals(stats.totalOrders(),
                stats.ordersByStatus().values().stream().mapToLong(Long::longValue).sum());
        when(orderingStatisticsApi.forOwnShop(11L)).thenReturn(stats);

        mockMvc.perform(get("/seller/dashboard").param("shopId", "99").with(user("seller").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/seller-dashboard"))
                .andExpect(model().attribute("productCount", 12L))
                .andExpect(model().attribute("totalOrders", 9L))
                .andExpect(model().attribute("ordersByStatus", statusCounts))
                .andExpect(model().attribute("completedRevenue", completedRevenue))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("1,234,567.89₫")));

        verify(merchantStatisticsApi).countProductsForCurrentShop();
        verify(merchantApi).findShopSummaryForOwner(7L);
        verify(orderingStatisticsApi).forOwnShop(11L);
    }

    @Test
    void dashboardDoesNotQueryOrderingWhenCurrentUserHasNoShop() throws Exception {
        when(merchantStatisticsApi.countProductsForCurrentShop()).thenReturn(0L);
        when(currentUserApi.requireCurrentUser())
                .thenReturn(new UserSummary(7L, "Shop owner", UserRole.USER, AccountStatus.ACTIVE));
        when(merchantApi.findShopSummaryForOwner(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/seller/dashboard").with(user("seller").roles("USER")))
                .andExpect(status().isNotFound());

        verifyNoInteractions(orderingStatisticsApi);
    }

    @Test
    void dashboardPropagatesOrderingAuthorizationFailure() throws Exception {
        when(merchantStatisticsApi.countProductsForCurrentShop()).thenReturn(0L);
        when(currentUserApi.requireCurrentUser())
                .thenReturn(new UserSummary(7L, "Shop owner", UserRole.USER, AccountStatus.ACTIVE));
        when(merchantApi.findShopSummaryForOwner(7L))
                .thenReturn(Optional.of(new MerchantShopSummary(11L, 7L, "Shop", true, true)));
        when(orderingStatisticsApi.forOwnShop(11L))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/seller/dashboard").with(user("seller").roles("USER")))
                .andExpect(status().isNotFound());

        verify(orderingStatisticsApi).forOwnShop(11L);
    }

    @Test
    void dashboardRejectsUserWithoutShopAccess() throws Exception {
        when(merchantStatisticsApi.countProductsForCurrentShop())
                .thenThrow(new MerchantStatisticsAccessException("No shop access."));

        mockMvc.perform(get("/seller/dashboard").with(user("seller").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardRequiresUserRole() throws Exception {
        mockMvc.perform(get("/seller/dashboard").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void listRendersDtoPageAndRealUnreadCount() throws Exception {
        NotificationListItem item = new NotificationListItem(
                1L, NotificationType.SHOP_REQUESTED, "Title", "Content", false, Instant.EPOCH);
        when(notificationService.getNotifications(1))
                .thenReturn(new NotificationPageView(List.of(item), 1, 1, 1, 1));

        mockMvc.perform(get("/account/notifications").with(user("receiver")))
                .andExpect(status().isOk())
                .andExpect(view().name("engagement/notifications"))
                .andExpect(model().attribute("notifications", List.of(item)))
                .andExpect(model().attribute("unreadNotifications", 1L));
    }

    @Test
    void markOneUsesPostRedirectGetAndKeepsNormalizedPage() throws Exception {
        when(notificationService.markRead(7L)).thenReturn(true);

        mockMvc.perform(post("/account/notifications/7/read")
                        .param("page", "0").with(user("receiver")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/notifications?page=1"))
                .andExpect(flash().attributeExists("successMessage"));
    }

    @Test
    void foreignOrMissingNotificationReturnsNotFound() throws Exception {
        when(notificationService.markRead(8L)).thenThrow(new NotificationNotFoundException());

        mockMvc.perform(post("/account/notifications/8/read")
                        .with(user("receiver")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void mutationWithoutCsrfIsForbidden() throws Exception {
        mockMvc.perform(post("/account/notifications/read-all").with(user("receiver")))
                .andExpect(status().isForbidden());
    }

    @Test
    void markAllUsesPostRedirectGetAndSupportsNoOp() throws Exception {
        when(notificationService.markAllRead()).thenReturn(0);

        mockMvc.perform(post("/account/notifications/read-all")
                        .param("page", "3").with(user("receiver")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/notifications?page=3"))
                .andExpect(flash().attribute("successMessage", "Bạn không có thông báo chưa đọc."));
    }
}
