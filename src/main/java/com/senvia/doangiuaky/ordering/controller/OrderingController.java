package com.senvia.doangiuaky.ordering.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import java.util.List;

import com.senvia.doangiuaky.ordering.dto.CheckoutPaymentForm;
import com.senvia.doangiuaky.ordering.dto.CheckoutSelectionView;
import com.senvia.doangiuaky.ordering.service.CheckoutSelectionException;
import com.senvia.doangiuaky.ordering.service.CheckoutSelectionService;
import com.senvia.doangiuaky.ordering.service.CheckoutPreviewService;
import com.senvia.doangiuaky.ordering.service.CheckoutPreparationException;
import com.senvia.doangiuaky.ordering.service.PreparedCheckout;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import jakarta.servlet.http.HttpSession;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.senvia.doangiuaky.ordering.service.CheckoutQuote;
import com.senvia.doangiuaky.ordering.service.CheckoutReceipt;
import com.senvia.doangiuaky.ordering.service.CheckoutTransactionService;

import com.senvia.doangiuaky.ordering.dto.SellerOrderView;
import com.senvia.doangiuaky.ordering.service.SellerOrderReadService;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.senvia.doangiuaky.ordering.service.SellerPaymentConfirmationService;

import com.senvia.doangiuaky.ordering.dto.SellerOrderPage;
import java.util.Locale;
import java.util.stream.IntStream;

import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.service.SellerOrderStatusService;
import com.senvia.doangiuaky.ordering.service.OrderCancellationService;
import com.senvia.doangiuaky.ordering.api.OrderHistoryApi;

@Controller
public class OrderingController {
    private final CheckoutSelectionService checkoutSelectionService;
    private final CheckoutPreviewService checkoutPreviewService;
    private final CheckoutTransactionService checkoutTransactionService;
    private final SellerOrderReadService sellerOrderReadService;
    private final SellerPaymentConfirmationService sellerPaymentConfirmationService;
    private final SellerOrderStatusService sellerOrderStatusService;
    private final OrderCancellationService orderCancellationService;
    private final OrderHistoryApi orderHistoryApi;

    private static final String SESSION_QUOTE = "ordering.checkout.quote";

    public OrderingController(CheckoutSelectionService checkoutSelectionService, CheckoutPreviewService checkoutPreviewService, CheckoutTransactionService checkoutTransactionService, SellerOrderReadService sellerOrderReadService, SellerPaymentConfirmationService sellerPaymentConfirmationService, SellerOrderStatusService sellerOrderStatusService, OrderCancellationService orderCancellationService, OrderHistoryApi orderHistoryApi) {
        this.checkoutSelectionService = checkoutSelectionService;
        this.checkoutPreviewService = checkoutPreviewService;
        this.checkoutTransactionService = checkoutTransactionService;
        this.sellerOrderReadService = sellerOrderReadService;
        this.sellerPaymentConfirmationService = sellerPaymentConfirmationService;
        this.sellerOrderStatusService = sellerOrderStatusService;
        this.orderCancellationService = orderCancellationService;
        this.orderHistoryApi = orderHistoryApi;
    }

    @GetMapping("/checkout")
    public String checkout(@RequestParam(name = "itemIds", required = false) List<String> itemIds, Model model,
            HttpSession session) {
        // Không sử dụng lại báo giá từ lần trước.
        session.removeAttribute(SESSION_QUOTE);

        model.addAttribute("checkoutPaymentForm", new CheckoutPaymentForm());
        populateSelection(itemIds, model);

        return "ordering/checkout/index";
    }

    @PostMapping("/checkout")
    public String validateCheckout(@Valid @ModelAttribute("checkoutPaymentForm") CheckoutPaymentForm form,
                                   BindingResult bindingResult,
                                   @RequestParam(name = "itemIds", required = false) List<String> itemIds,
            Model model, HttpSession session) {

        // Xóa báo giá cũ để không xác nhận nhầm.
        session.removeAttribute(SESSION_QUOTE);

        List<Long> selectedItemIds = populateSelection(itemIds, model);

        // Kiểm tra dữ liệu form và item đã chọn.
        if (bindingResult.hasErrors() || selectedItemIds == null) {
            return "ordering/checkout/index";
        }

        // Chuẩn hóa thông tin giao hàng.
        form.setReceiverName(form.getReceiverName().strip());
        form.setReceiverPhone(form.getReceiverPhone().strip());
        form.setShippingAddress(form.getShippingAddress().strip());

        if (form.getNote() == null || form.getNote().isBlank()) {
            form.setNote(null);
        } else {
            form.setNote(form.getNote().strip());
        }

        try {
            PreparedCheckout preview = checkoutPreviewService.preview(form, selectedItemIds);
            model.addAttribute("checkoutPreview", preview);

            // Lưu báo giá vào session để xác nhận sau.
            saveQuote(session, model, preview);
        } catch (CheckoutPreparationException exception) {
            model.addAttribute("checkoutError", exception.getMessage());
        }

        return "ordering/checkout/index";
    }

    @PostMapping("/checkout/confirm")
    public String confirmCheckout(@RequestParam(name = "checkoutToken", required = false) String token, HttpSession session, Model model, RedirectAttributes redirectAttributes) {
        CheckoutQuote quote = (CheckoutQuote) session.getAttribute(SESSION_QUOTE);

        // Không cho đặt hàng nếu chưa xem trước
        // hoặc token đã hết hạn.
        if (quote == null || !quote.validToken(token)) {
            session.removeAttribute(SESSION_QUOTE);
            return "redirect:/cart";
        }

        try {
            // Thực hiện checkout transaction.
            CheckoutReceipt receipt = checkoutTransactionService.placeOrder(quote);

            // Đặt thành công: hủy token cũ.
            session.removeAttribute(SESSION_QUOTE);

            redirectAttributes.addFlashAttribute("checkoutReceipt", receipt);

            return "redirect:/checkout/success";
        } catch (RuntimeException ex) {
            // Giao dịch đã bị rollback ở service.
            session.removeAttribute(SESSION_QUOTE);

            // Hiển thị lại thông tin người mua.
            model.addAttribute("checkoutPaymentForm", quote.toForm());

            populateSelection(
                    quote.selectedItemIds()
                            .stream()
                            .map(String::valueOf)
                            .toList(),
                    model
            );

            // Thông báo lỗi cho người mua.
            model.addAttribute("checkoutError", ex instanceof CheckoutPreparationException ? ex.getMessage() : "Không thể đặt hàng do kho/giỏ " + "hoặc hệ thống thay đổi. " + "Vui lòng kiểm tra lại."
            );

            // Nếu dữ liệu còn hợp lệ, tạo báo giá mới.
            try {
                PreparedCheckout refreshed = checkoutPreviewService.preview(quote.toForm(), quote.selectedItemIds());

                model.addAttribute("checkoutPreview", refreshed);

                saveQuote(session, model, refreshed);

            } catch (RuntimeException ignored) {
                // Không cấp token xác nhận nếu
                // các sản phẩm hiện không thể bán.
            }

            return "ordering/checkout/index";
        }
    }

    private List<Long> populateSelection(List<String> itemIds, Model model) {
        try {
            CheckoutSelectionView selection = checkoutSelectionService.preview(itemIds);
            model.addAttribute("checkoutSelection", selection);
            List<Long> selectedIds = selection.shops().stream()
                    .flatMap(shop -> shop.items().stream())
                    .map(CheckoutSelectionView.Item::cartItemId)
                    .toList();
            model.addAttribute("selectedItemIds", selectedIds);
            return selectedIds;
        } catch (CheckoutSelectionException exception) {
            model.addAttribute("checkoutSelectionError", exception.getMessage());
            return null;
        }
    }

    @GetMapping("/checkout/success")
    public String checkoutSuccess(Model model) {
        if (!model.containsAttribute("checkoutReceipt")) {
            return "redirect:/account/orders";
        }

        return "ordering/checkout-success";
    }

    @GetMapping("/account/orders")
    public String orders() { return "ordering/orders"; }

    @GetMapping("/account/orders/detail")
    public String orderDetail() { return "ordering/order-detail"; }

    @GetMapping("/merchant/orders")
    public String merchantOrders(@RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page, Model model) {
        SellerOrderPage result = sellerOrderReadService.pageOwnOrders(status, page);

        model.addAttribute("orders", result.orders());
        model.addAttribute("pendingCount", result.pendingCount());
        model.addAttribute("totalOrders", result.totalElements());
        model.addAttribute("currentPage", result.currentPage());
        model.addAttribute("totalPages", result.totalPages());

        String selectedStatus = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);

        model.addAttribute("selectedStatus", selectedStatus);

        int firstPage = Math.max(1, result.currentPage() - 2);

        int lastPage = Math.min(result.totalPages(), result.currentPage() + 2);

        model.addAttribute(
                "pageNumbers",
                IntStream.rangeClosed(firstPage, lastPage)
                        .boxed()
                        .toList()
        );

        return "ordering/seller-orders";
    }

    @GetMapping("/merchant/orders/detail")
    public String merchantOrderDetail(@RequestParam String id, Model model) {
        SellerOrderView order = sellerOrderReadService.getOwnOrder(id);

        model.addAttribute("order", order);
        model.addAttribute("paymentDetails", order.payment());
        model.addAttribute("status", order.status());
        model.addAttribute("history", orderHistoryApi.forCurrentSeller(id));

        String nextStatus = switch (order.status()) {
            case "PENDING" -> "CONFIRMED";
            case "CONFIRMED" -> "PREPARING";
            case "PREPARING" -> "SHIPPING";
            case "SHIPPING" -> "COMPLETED";
            default -> null;
        };

        boolean paymentReady =
                (order.payment().method() == PaymentMethod.BANK_TRANSFER && order.payment().status() == PaymentStatus.PAID)
                || (order.payment().method() == PaymentMethod.COD && order.payment().status() == PaymentStatus.COD_PENDING);

        String nextActionLabel = nextStatus == null
                ? null
                : switch (nextStatus) {
                    case "CONFIRMED" -> "Xác nhận đơn hàng";
                    case "PREPARING" -> "Bắt đầu chuẩn bị hàng";
                    case "SHIPPING" -> "Bàn giao đơn cho vận chuyển";
                    case "COMPLETED" -> "Hoàn thành đơn hàng";
                    default -> null;
                };

        model.addAttribute("nextStatus", nextStatus);
        model.addAttribute("nextActionLabel", nextActionLabel);
        model.addAttribute("canAdvance", nextStatus != null && paymentReady);

        return "ordering/seller-order-detail";
    }

    // PAY 05: Chủ shop xác nhận đã nhận tiền mô phỏng.
    @PostMapping("/merchant/orders/payment/confirm")
    public String confirmSellerPayment(@RequestParam("id") String orderCode, RedirectAttributes redirectAttributes) {
        try {
            sellerPaymentConfirmationService.confirmBankTransfer(orderCode);
            redirectAttributes.addFlashAttribute("paymentSuccess", "Đã ghi nhận thanh toán mô phỏng.");
        } catch (ResponseStatusException exception) {
            // Các lỗi trạng thái được hiển thị lại
            // trên trang chi tiết đơn hàng.
            if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw exception;
            }

            redirectAttributes.addFlashAttribute("paymentError", exception.getReason());
        }

        redirectAttributes.addAttribute("id", orderCode);

        return "redirect:/merchant/orders/detail";
    }

    @GetMapping("/merchant/orders/status")
    public String updateOrderStatus() {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Cập nhật trạng thái đơn hàng chưa được kết nối với dữ liệu thật.");
    }

    // ORDER 04: Chủ shop chuyển trạng thái đơn hàng.
    @PostMapping("/merchant/orders/status")
    public String updateSellerOrderStatus(@RequestParam("id") String orderCode,
            @RequestParam("status") OrderStatus nextStatus, RedirectAttributes redirectAttributes) {
        try {
            sellerOrderStatusService.changeStatus(orderCode, nextStatus);
            redirectAttributes.addFlashAttribute("statusSuccess", "Đã cập nhật trạng thái đơn hàng thành công.");
        } catch (ResponseStatusException exception) {
            // Không che giấu lỗi phân quyền hoặc đơn không tồn tại.
            if (exception.getStatusCode().value() != HttpStatus.CONFLICT.value()) {
                throw exception;
            }

            redirectAttributes.addFlashAttribute("statusError", exception.getReason());
        }

        redirectAttributes.addAttribute("id", orderCode);
        return "redirect:/merchant/orders/detail";
    }

    // ORDER 08 - Chủ shop hủy đơn.
    @PostMapping("/merchant/orders/cancel")
    public String cancelSellerOrder(@RequestParam("id") String code, @RequestParam("reason") String reason, RedirectAttributes redirectAttributes) {
        try {
            orderCancellationService.cancelAsSeller(code, reason);
            redirectAttributes.addFlashAttribute("cancelSuccess", "Đã hủy đơn và hoàn kho thành công.");
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();

            if (status != 400 && status != 409) {
                throw exception;
            }

            redirectAttributes.addFlashAttribute("cancelError", exception.getReason());
        }

        redirectAttributes.addAttribute("id", code);
        return "redirect:/merchant/orders/detail";
    }

    // ORDER 08 - Buyer hủy đơn thuộc quyền sở hữu.
    @PostMapping("/account/orders/cancel")
    public String cancelBuyerOrder(@RequestParam("id") String code, @RequestParam("reason") String reason, RedirectAttributes redirectAttributes) {
        try {
            orderCancellationService.cancelAsBuyer(code, reason);
            redirectAttributes.addFlashAttribute("cancelSuccess", "Đã hủy đơn hàng.");
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            if (status != 400 && status != 409) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("cancelError", exception.getReason());
        }
        return "redirect:/account/orders";
    }

    // ORDER 08 - Admin hủy đơn hợp lệ.
    @PostMapping("/admin/orders/cancel")
    public String cancelAdminOrder(@RequestParam("id") String code, @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {
        try {
            orderCancellationService.cancelAsAdmin(code, reason);
            redirectAttributes.addFlashAttribute("cancelSuccess", "Đã hủy đơn hàng.");
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            if (status != 400 && status != 409) {
                throw exception;
            }
            redirectAttributes.addFlashAttribute("cancelError", exception.getReason());
        }
        return "redirect:/admin/orders";
    }

    // ORDER 07: Buyer xem lịch sử đơn thuộc sở hữu.
    @GetMapping("/account/orders/history")
    public String buyerOrderHistory(@RequestParam("id") String orderCode, Model model) {
        var history = orderHistoryApi.forCurrentBuyer(orderCode);

        model.addAttribute("orderCode", orderCode);
        model.addAttribute("history", history);
        model.addAttribute("backUrl", "/account/orders");

        return "ordering/order-history";
    }

    // ORDER 07: Admin xem lịch sử đơn.
    @GetMapping("/admin/orders/history")
    public String adminOrderHistory(@RequestParam("id") String orderCode, Model model) {
        var history = orderHistoryApi.forCurrentAdmin(orderCode);

        model.addAttribute("orderCode", orderCode);
        model.addAttribute("history", history);
        model.addAttribute("backUrl", "/admin/orders");

        return "ordering/order-history";
    }

    @GetMapping("/admin/orders")
    public String adminOrders() {
        return "ordering/admin-orders";
    }

    private void saveQuote(HttpSession session, Model model, PreparedCheckout preview) {
        CheckoutQuote quote = CheckoutQuote.of(preview);

        session.setAttribute(SESSION_QUOTE, quote);

        model.addAttribute("checkoutToken", quote.token());
    }
}
