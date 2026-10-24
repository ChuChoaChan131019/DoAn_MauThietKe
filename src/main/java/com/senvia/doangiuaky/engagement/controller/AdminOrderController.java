package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.ordering.api.AdminOrderApi;
import com.senvia.doangiuaky.ordering.api.OrderCancellationApi;
import com.senvia.doangiuaky.ordering.api.OrderDetails;
import com.senvia.doangiuaky.ordering.api.OrderPage;
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

    private static final List<String> ORDER_STATUSES = List.of(
            "PENDING", "CONFIRMED", "PREPARING", "SHIPPING", "COMPLETED", "CANCELLED");
    private static final List<String> CANCELLABLE_STATUSES = List.of(
            "PENDING", "CONFIRMED", "PREPARING");

    private final AdminOrderApi adminOrderApi;
    private final OrderCancellationApi orderCancellationApi;

    public AdminOrderController(AdminOrderApi adminOrderApi, OrderCancellationApi orderCancellationApi) {
        this.adminOrderApi = adminOrderApi;
        this.orderCancellationApi = orderCancellationApi;
    }

    @GetMapping("/admin/orders/monitor")
    public String orders(@RequestParam(required = false) String status,
                         @RequestParam(required = false) Long buyerId,
                         @RequestParam(required = false) Long shopId,
                         @RequestParam(required = false) String orderCode,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "20") int size,
                         Model model) {
        OrderPage orderPage = adminOrderApi.listOrders(status, buyerId, shopId, orderCode, page, size);
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("statusOptions", ORDER_STATUSES);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("buyerId", buyerId);
        model.addAttribute("shopId", shopId);
        model.addAttribute("orderCode", orderCode);
        model.addAttribute("pageSize", size);
        return "engagement/admin-orders";
    }

    @GetMapping("/admin/orders/monitor/{orderCode}")
    public String orderDetails(@PathVariable String orderCode, Model model) {
        OrderDetails details = adminOrderApi.getOrder(orderCode);
        model.addAttribute("orderDetails", details);
        model.addAttribute("canCancel", CANCELLABLE_STATUSES.contains(details.order().status()));
        return "engagement/admin-order-detail";
    }

    @PostMapping("/admin/orders/monitor/{orderCode}/cancel")
    public String cancelOrder(@PathVariable String orderCode,
                              @RequestParam String reason,
                              RedirectAttributes redirectAttributes) {
        try {
            orderCancellationApi.cancelAsAdmin(orderCode, reason);
            redirectAttributes.addFlashAttribute("cancelSuccess", "Đã gửi yêu cầu hủy đơn.");
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            if (status != HttpStatus.BAD_REQUEST.value() && status != HttpStatus.CONFLICT.value()) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("cancelError", exception.getReason());
            redirectAttributes.addFlashAttribute("cancelReason", reason);
        }
        return "redirect:/admin/orders/monitor/" + orderCode;
    }
}
