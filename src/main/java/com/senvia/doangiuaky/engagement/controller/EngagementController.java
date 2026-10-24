package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.NotificationPageView;
import com.senvia.doangiuaky.engagement.service.NotificationService;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsAccessException;
import com.senvia.doangiuaky.merchant.api.MerchantStatisticsApi;
import com.senvia.doangiuaky.ordering.api.OrderingStatisticsApi;
import com.senvia.doangiuaky.ordering.api.ShopOrderStatistics;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class EngagementController {

    private final NotificationService notificationService;
    private final MerchantStatisticsApi merchantStatisticsApi;
    private final CurrentUserApi currentUserApi;
    private final MerchantApi merchantApi;
    private final OrderingStatisticsApi orderingStatisticsApi;

    public EngagementController(NotificationService notificationService,
                                MerchantStatisticsApi merchantStatisticsApi,
                                CurrentUserApi currentUserApi,
                                MerchantApi merchantApi,
                                OrderingStatisticsApi orderingStatisticsApi) {
        this.notificationService = notificationService;
        this.merchantStatisticsApi = merchantStatisticsApi;
        this.currentUserApi = currentUserApi;
        this.merchantApi = merchantApi;
        this.orderingStatisticsApi = orderingStatisticsApi;
    }

    @GetMapping("/seller/dashboard")
    public String sellerDashboard(Model model) {
        try {
            model.addAttribute("productCount", merchantStatisticsApi.countProductsForCurrentShop());
            Long ownerId = currentUserApi.requireCurrentUser().userId();
            Long shopId = merchantApi.findShopSummaryForOwner(ownerId)
                    .map(shop -> shop.shopId())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Không tìm thấy gian hàng của bạn."));
            ShopOrderStatistics orderStatistics = orderingStatisticsApi.forOwnShop(shopId);
            model.addAttribute("totalOrders", orderStatistics.totalOrders());
            model.addAttribute("ordersByStatus", orderStatistics.ordersByStatus());
            model.addAttribute("orderStatusLabels", orderStatusLabels());
            return "engagement/seller-dashboard";
        } catch (MerchantStatisticsAccessException exception) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, exception.getMessage(), exception);
        }
    }

    private static Map<String, String> orderStatusLabels() {
        return Map.of(
                "PENDING", "Chờ xác nhận",
                "CONFIRMED", "Đã xác nhận",
                "PREPARING", "Đang chuẩn bị",
                "SHIPPING", "Đang giao",
                "COMPLETED", "Hoàn tất",
                "CANCELLED", "Đã hủy");
    }

    @GetMapping("/account/notifications")
    public String notifications(@RequestParam(defaultValue = "1") int page, Model model) {
        NotificationPageView notificationPage = notificationService.getNotifications(page);
        model.addAttribute("notificationPage", notificationPage);
        model.addAttribute("notifications", notificationPage.items());
        model.addAttribute("unreadNotifications", notificationPage.unreadCount());
        return "engagement/notifications";
    }

    @PostMapping("/account/notifications/{id}/read")
    public String markRead(@PathVariable Long id,
                           @RequestParam(defaultValue = "1") int page,
                           RedirectAttributes redirectAttributes) {
        boolean changed = notificationService.markRead(id);
        redirectAttributes.addFlashAttribute("successMessage", changed
                ? "Đã đánh dấu thông báo là đã đọc."
                : "Thông báo này đã được đánh dấu là đã đọc.");
        return redirectToPage(page);
    }

    @PostMapping("/account/notifications/read-all")
    public String markAllRead(@RequestParam(defaultValue = "1") int page,
                              RedirectAttributes redirectAttributes) {
        int changed = notificationService.markAllRead();
        redirectAttributes.addFlashAttribute("successMessage", changed > 0
                ? "Đã đánh dấu tất cả thông báo là đã đọc."
                : "Bạn không có thông báo chưa đọc.");
        return redirectToPage(page);
    }

    private String redirectToPage(int page) {
        int normalizedPage = Math.max(page, 1);
        return "redirect:/account/notifications?page=" + normalizedPage;
    }

    @GetMapping("/account/review")
    public String review() { return "engagement/review-form"; }
}
