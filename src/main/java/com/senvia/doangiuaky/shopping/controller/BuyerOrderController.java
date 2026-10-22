package com.senvia.doangiuaky.shopping.controller;

import com.senvia.doangiuaky.ordering.api.BuyerOrderApi;
import com.senvia.doangiuaky.ordering.api.OrderCancellationApi;
import com.senvia.doangiuaky.ordering.api.OrderDetails;
import com.senvia.doangiuaky.ordering.api.OrderPage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.IntStream;

@Controller
public class BuyerOrderController {
    private static final int PAGE_SIZE = 10;
    private static final DateTimeFormatter ORDER_DATE_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final Map<String, String> STATUS_LABELS = statusLabels();
    private static final Map<String, String> PAYMENT_METHOD_LABELS = paymentMethodLabels();
    private static final Map<String, String> PAYMENT_STATUS_LABELS = paymentStatusLabels();

    private final BuyerOrderApi buyerOrderApi;
    private final OrderCancellationApi orderCancellationApi;

    public BuyerOrderController(BuyerOrderApi buyerOrderApi, OrderCancellationApi orderCancellationApi) {
        this.buyerOrderApi = buyerOrderApi;
        this.orderCancellationApi = orderCancellationApi;
    }

    @GetMapping("/account/orders")
    public String list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            Model model) {
        String normalizedStatus = normalizeStatus(status);
        OrderPage orderPage = buyerOrderApi.listOwnOrders(normalizedStatus, page, PAGE_SIZE);
        if (page > orderPage.totalPages()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trang không hợp lệ.");
        }

        model.addAttribute("orders", orderPage.orders());
        model.addAttribute("currentPage", orderPage.currentPage());
        model.addAttribute("totalPages", orderPage.totalPages());
        model.addAttribute("totalElements", orderPage.totalElements());
        model.addAttribute("selectedStatus", normalizedStatus);
        model.addAttribute("statusOptions", STATUS_LABELS);
        model.addAttribute("pageNumbers", pageNumbers(orderPage));
        model.addAttribute("orderDates", orderDates(orderPage));

        return "shopping/orders";
    }

    @GetMapping("/account/orders/detail")
    public String detail(@RequestParam("id") String orderCode, Model model) {
        OrderDetails order = buyerOrderApi.getOwnOrder(orderCode);

        model.addAttribute("order", order);
        model.addAttribute("statusLabels", STATUS_LABELS);
        model.addAttribute("paymentMethodLabels", PAYMENT_METHOD_LABELS);
        model.addAttribute("paymentStatusLabels", PAYMENT_STATUS_LABELS);
        model.addAttribute("orderCreatedAt", formatInstant(order.order().createdAt()));
        model.addAttribute("backUrl", "/account/orders");

        return "shopping/order-detail";
    }

    @PostMapping("/account/orders/cancel")
    public String cancel(
            @RequestParam("id") String orderCode,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {
        try {
            orderCancellationApi.cancelAsBuyer(orderCode, reason);
            redirectAttributes.addFlashAttribute("cancelSuccess", "Đã hủy đơn hàng.");
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            if (status != HttpStatus.BAD_REQUEST.value() && status != HttpStatus.CONFLICT.value()) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("cancelError", exception.getReason());
        }

        redirectAttributes.addAttribute("id", orderCode);
        return "redirect:/account/orders/detail";
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.strip().toUpperCase(Locale.ROOT);
    }

    private static java.util.List<Integer> pageNumbers(OrderPage page) {
        int firstPage = Math.max(1, page.currentPage() - 2);
        int lastPage = Math.min(page.totalPages(), page.currentPage() + 2);
        return IntStream.rangeClosed(firstPage, lastPage).boxed().toList();
    }

    private static Map<String, String> orderDates(OrderPage page) {
        Map<String, String> dates = new LinkedHashMap<>();
        page.orders().forEach(order -> dates.put(order.orderCode(), formatInstant(order.createdAt())));
        return dates;
    }

    private static String formatInstant(Instant value) {
        return value == null ? "" : ORDER_DATE_FORMAT.format(value);
    }

    private static Map<String, String> statusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("PENDING", "Chờ xử lý");
        labels.put("CONFIRMED", "Đã xác nhận");
        labels.put("PREPARING", "Đang chuẩn bị");
        labels.put("SHIPPING", "Đang giao hàng");
        labels.put("COMPLETED", "Đã hoàn tất");
        labels.put("CANCELLED", "Đã hủy");
        return Collections.unmodifiableMap(labels);
    }

    private static Map<String, String> paymentMethodLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("COD", "Thanh toán khi nhận hàng");
        labels.put("BANK_TRANSFER", "Chuyển khoản mô phỏng");
        return Collections.unmodifiableMap(labels);
    }

    private static Map<String, String> paymentStatusLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("PENDING", "Chờ thanh toán");
        labels.put("FAILED", "Thanh toán thất bại");
        labels.put("COD_PENDING", "Chờ thanh toán khi nhận hàng");
        labels.put("PAID", "Đã thanh toán");
        labels.put("REFUNDED", "Đã hoàn tiền");
        labels.put("CANCELLED", "Đã hủy");
        return Collections.unmodifiableMap(labels);
    }
}
