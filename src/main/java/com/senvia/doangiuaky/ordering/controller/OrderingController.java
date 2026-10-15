package com.senvia.doangiuaky.ordering.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import java.util.HashMap;
import java.util.Map;

import java.util.ArrayList;
import java.util.List;

import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;
import com.senvia.doangiuaky.ordering.dto.CheckoutSelectionView;
import com.senvia.doangiuaky.ordering.service.CheckoutSelectionException;
import com.senvia.doangiuaky.ordering.service.CheckoutSelectionService;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class OrderingController {

    private final CheckoutSelectionService checkoutSelectionService;

    public OrderingController(CheckoutSelectionService checkoutSelectionService) {
        this.checkoutSelectionService = checkoutSelectionService;
    }

    private static final List<Map<String, Object>> mockOrders = new ArrayList<>();

    static {
        Map<String, Object> order1 = new HashMap<>();
        order1.put("id", "ORD-20231025-001");
        order1.put("date", "25/10/2023 14:30");
        order1.put("customer", "Nguyễn Văn A");
        order1.put("phone", "0901234567");
        order1.put("address", "123 Đường Lê Lợi, Phường Bến Thành, Quận 1, TP. Hồ Chí Minh");
        order1.put("payment", "COD");
        order1.put("status", "PENDING");
        order1.put("total", "4.530.000₫");
        order1.put("productName", "Nước Hoa Le Labo Santal 33");
        order1.put("productVariant", "Phân loại: 50ml | Mã SP: PRD-001");
        order1.put("productPrice", "4.500.000₫");
        order1.put("productImage", "https://images.unsplash.com/photo-1594035910387-fea47714263f?w=150&q=80");
        order1.put("subTotal", "4.500.000₫");
        order1.put("shippingFee", "30.000₫");
        order1.put("platformFee", "-90.000₫");
        order1.put("finalTotal", "4.410.000₫");
        mockOrders.add(order1);

        Map<String, Object> order2 = new HashMap<>();
        order2.put("id", "ORD-20231025-002");
        order2.put("date", "25/10/2023 10:15");
        order2.put("customer", "Trần Thị B");
        order2.put("phone", "0912345678");
        order2.put("address", "456 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh");
        order2.put("payment", "BANK_TRANSFER");
        order2.put("status", "CONFIRMED");
        order2.put("total", "1.280.000₫");
        order2.put("productName", "Túi Xách Da Minimalist");
        order2.put("productVariant", "Phân loại: Đen | Mã SP: PRD-002");
        order2.put("productPrice", "1.250.000₫");
        order2.put("productImage", "https://images.unsplash.com/photo-1490481651871-ab68de25d43d?w=150&q=80");
        order2.put("subTotal", "1.250.000₫");
        order2.put("shippingFee", "30.000₫");
        order2.put("platformFee", "-25.000₫");
        order2.put("finalTotal", "1.255.000₫");
        mockOrders.add(order2);
    }

    @GetMapping("/checkout")
    public String checkout(@RequestParam(name = "itemIds", required = false) List<String> itemIds, Model model) {
        model.addAttribute(
                "checkoutPaymentForm",
                new CheckoutPaymentForm());
        populateSelection(itemIds, model);

        return "ordering/checkout/index";
    }

    @PostMapping("/checkout")
    public String validateCheckout(@Valid @ModelAttribute("checkoutPaymentForm") CheckoutPaymentForm form,
                                   BindingResult bindingResult,
                                   @RequestParam(name = "itemIds", required = false) List<String> itemIds,
                                   Model model) {
        boolean selectionValid = populateSelection(itemIds, model);
        if (bindingResult.hasErrors() || !selectionValid) {
            return "ordering/checkout/index";
        }

        // Chuẩn hóa thông tin giao hàng.
        form.setReceiverName(form.getReceiverName().trim());
        form.setReceiverPhone(form.getReceiverPhone().trim());
        form.setShippingAddress(form.getShippingAddress().trim());

        if (form.getNote() == null || form.getNote().isBlank()) {
            form.setNote(null);
        } else {
            form.setNote(form.getNote().trim());
        }

        // PAY 01 và PAY 03: dữ liệu hợp lệ; chưa tạo đơn hàng.
        model.addAttribute("addressValidated", true);
        model.addAttribute("paymentValidated", true);

        return "ordering/checkout/index";
    }

    private boolean populateSelection(List<String> itemIds, Model model) {
        try {
            CheckoutSelectionView selection = checkoutSelectionService.preview(itemIds);
            model.addAttribute("checkoutSelection", selection);
            model.addAttribute("selectedItemIds", selection.shops().stream()
                    .flatMap(shop -> shop.items().stream())
                    .map(CheckoutSelectionView.Item::cartItemId)
                    .toList());
            return true;
        } catch (CheckoutSelectionException exception) {
            model.addAttribute("checkoutSelectionError", exception.getMessage());
            return false;
        }
    }

    @GetMapping("/checkout/success")
    public String checkoutSuccess() {
        return "ordering/checkout-success";
    }

    @GetMapping("/account/orders")
    public String orders() { return "ordering/orders"; }

    @GetMapping("/account/orders/detail")
    public String orderDetail() { return "ordering/order-detail"; }

    @GetMapping("/merchant/orders")
    public String merchantOrders(@RequestParam(required = false) String status, Model model) {
        List<Map<String, Object>> filtered = mockOrders;
        if (status != null && !status.trim().isEmpty()) {
            filtered = mockOrders.stream().filter(o -> status.equals(o.get("status"))).toList();
        }
        model.addAttribute("orders", filtered);
        long pendingCount = mockOrders.stream().filter(o -> "PENDING".equals(o.get("status"))).count();
        model.addAttribute("pendingCount", pendingCount);
        return "ordering/seller-orders";
    }

    @GetMapping("/merchant/orders/detail")
    public String merchantOrderDetail(@RequestParam(defaultValue = "ORD-20231025-001") String id, Model model) {
        Map<String, Object> order = mockOrders.stream()
            .filter(o -> o.get("id").equals(id))
            .findFirst()
            .orElse(mockOrders.get(0));

        model.addAttribute("order", order);
        model.addAttribute("orderId", order.get("id"));
        model.addAttribute("payment", order.get("payment"));
        model.addAttribute("status", order.get("status"));
        return "ordering/seller-order-detail";
    }

    @GetMapping("/merchant/orders/status")
    public String updateOrderStatus(@RequestParam String id, @RequestParam String action, @RequestParam(required = false) String reason, @RequestParam(required = false) String redirect) {
        String newStatus = "PENDING";
        switch (action) {
            case "CONFIRM_PAYMENT": newStatus = "CONFIRMED"; break;
            case "PREPARE": newStatus = "PREPARING"; break;
            case "SHIP": newStatus = "SHIPPING"; break;
            case "DELIVERED": newStatus = "COMPLETED"; break;
            case "CANCEL": newStatus = "CANCELLED"; break;
        }

        for (Map<String, Object> order : mockOrders) {
            if (order.get("id").equals(id)) {
                order.put("status", newStatus);
                break;
            }
        }

        if (redirect != null) return "redirect:" + redirect;
        return "redirect:/merchant/orders/detail?id=" + id;
    }

    @GetMapping("/admin/orders")
    public String adminOrders() { return "ordering/admin-orders"; }
}


