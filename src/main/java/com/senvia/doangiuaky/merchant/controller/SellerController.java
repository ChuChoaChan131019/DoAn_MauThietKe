package com.senvia.doangiuaky.merchant.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SellerController {

    @GetMapping("/seller")
    public String seller() {
        return "redirect:/merchant/dashboard";
    }

    @GetMapping("/merchant/shop-public")
    public String shopPublic() { return "merchant/shop-public"; }

    @GetMapping("/merchant/dashboard")
    public String merchantDashboard() { return "merchant/dashboard"; }

    public String merchantOrders() { return "ordering/seller-orders"; }

    
    public String merchantOrderDetail() { return "ordering/seller-order-detail"; }

    @GetMapping("/merchant/profile")
    public String merchantProfile() { return "merchant/shop-info"; }
}
