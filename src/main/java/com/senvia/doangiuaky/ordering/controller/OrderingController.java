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

@Controller
public class OrderingController {
    private final CheckoutSelectionService checkoutSelectionService;
    private final CheckoutPreviewService checkoutPreviewService;
    private final CheckoutTransactionService checkoutTransactionService;
    private final SellerOrderReadService sellerOrderReadService;

    private static final String SESSION_QUOTE = "ordering.checkout.quote";

    public OrderingController(CheckoutSelectionService checkoutSelectionService, CheckoutPreviewService checkoutPreviewService, CheckoutTransactionService checkoutTransactionService, SellerOrderReadService sellerOrderReadService) {
        this.checkoutSelectionService = checkoutSelectionService;
        this.checkoutPreviewService = checkoutPreviewService;
        this.checkoutTransactionService = checkoutTransactionService;
        this.sellerOrderReadService = sellerOrderReadService;
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
    public String merchantOrders(@RequestParam(required = false) String status, Model model) {
        List<SellerOrderView> all = sellerOrderReadService.listOwnOrders(null);

        List<SellerOrderView> filtered = all.stream()
                .filter(order ->
                        status == null
                        || status.isBlank()
                        || order.status().equals(status))
                .toList();

        model.addAttribute("orders", filtered);

        model.addAttribute(
                "pendingCount",
                all.stream()
                        .filter(order -> "PENDING".equals(order.status()))
                        .count()
        );

        return "ordering/seller-orders";
    }

    @GetMapping("/merchant/orders/detail")
    public String merchantOrderDetail(@RequestParam String id, Model model) {
        SellerOrderView order = sellerOrderReadService.getOwnOrder(id);

        model.addAttribute("order", order);
        model.addAttribute("paymentDetails", order.payment());
        model.addAttribute("status", order.status());

        return "ordering/seller-order-detail";
    }

    @GetMapping("/merchant/orders/status")
    public String updateOrderStatus() {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "Cập nhật trạng thái đơn hàng chưa được kết nối với dữ liệu thật.");
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