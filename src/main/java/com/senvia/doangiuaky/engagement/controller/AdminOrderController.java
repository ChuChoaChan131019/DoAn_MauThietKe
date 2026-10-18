package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.ordering.api.AdminOrderApi;
import com.senvia.doangiuaky.ordering.api.OrderCancellationApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class AdminOrderController {

    private static final int PAGE_SIZE = 20;
    private static final List<String> STATUSES =
            List.of("PENDING", "CONFIRMED", "PREPARING", "SHIPPING", "COMPLETED", "CANCELLED");

    private final AdminOrderApi adminOrderApi;
    private final OrderCancellationApi orderCancellationApi;

    public AdminOrderController(AdminOrderApi adminOrderApi, OrderCancellationApi orderCancellationApi) {
        this.adminOrderApi = adminOrderApi;
        this.orderCancellationApi = orderCancellationApi;
    }

    @GetMapping("/engagement/admin/orders")
    public String orders(@RequestParam(required = false) String status,
                         @RequestParam(required = false) Long buyerId,
                         @RequestParam(required = false) Long shopId,
                         @RequestParam(required = false) String orderCode,
                         @RequestParam(defaultValue = "1") int page,
                         Model model) {
        model.addAttribute("ordersPage", adminOrderApi.listOrders(
                status, buyerId, shopId, orderCode, page, PAGE_SIZE));
        model.addAttribute("statusFilter", status);
        model.addAttribute("buyerIdFilter", buyerId);
        model.addAttribute("shopIdFilter", shopId);
        model.addAttribute("orderCodeFilter", orderCode);
        model.addAttribute("statuses", STATUSES);
        return "engagement/admin-orders";
    }

    @GetMapping("/engagement/admin/orders/{orderCode}")
    public String orderDetails(@PathVariable String orderCode, Model model) {
        model.addAttribute("orderDetails", adminOrderApi.getOrder(orderCode));
        return "engagement/admin-order-detail";
    }

    @PostMapping("/engagement/admin/orders/{orderCode}/cancel")
    public String cancelOrder(@PathVariable String orderCode,
                              @RequestParam String reason,
                              RedirectAttributes redirectAttributes) {
        try {
            orderCancellationApi.cancelAsAdmin(orderCode, reason);
            redirectAttributes.addFlashAttribute("cancelSuccess", "Đã hủy đơn hàng.");
        } catch (ResponseStatusException exception) {
            int statusCode = exception.getStatusCode().value();
            if (statusCode != HttpStatus.BAD_REQUEST.value()
                    && statusCode != HttpStatus.CONFLICT.value()) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("cancelError", exception.getReason());
        }
        return "redirect:/engagement/admin/orders/" + orderCode;
    }
}
