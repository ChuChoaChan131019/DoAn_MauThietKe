package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.merchant.api.MerchantShopRequestApi;
import com.senvia.doangiuaky.merchant.api.PendingShopRequestCard;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class AdminController {

    private final MerchantShopRequestApi merchantShopRequestApi;

    public AdminController(MerchantShopRequestApi merchantShopRequestApi) {
        this.merchantShopRequestApi = merchantShopRequestApi;
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        List<PendingShopRequestCard> pendingShopRequests = merchantShopRequestApi.findPendingShopRequests();
        model.addAttribute("pendingCount", pendingShopRequests.size());
        model.addAttribute("pendingShopRequests", pendingShopRequests);
        return "engagement/admin-dashboard";
    }

    @GetMapping("/admin/accounts")
    public String adminAccounts() {
        return "redirect:/identity/admin/accounts";
    }

}
