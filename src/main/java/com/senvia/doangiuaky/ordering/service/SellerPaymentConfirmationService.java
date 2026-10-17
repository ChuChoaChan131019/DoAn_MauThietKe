package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.api.CurrentUserApi;
import com.senvia.doangiuaky.identity.api.UserRole;
import com.senvia.doangiuaky.identity.api.UserSummary;
import com.senvia.doangiuaky.merchant.api.MerchantApi;
import com.senvia.doangiuaky.merchant.api.MerchantShopSummary;
import com.senvia.doangiuaky.ordering.entity.Order;
import com.senvia.doangiuaky.ordering.entity.OrderStatus;
import com.senvia.doangiuaky.ordering.entity.Payment;
import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import com.senvia.doangiuaky.ordering.entity.PaymentStatus;
import com.senvia.doangiuaky.ordering.repository.OrderRepository;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

@Service
public class SellerPaymentConfirmationService {
    private final CurrentUserApi currentUserApi;
    private final MerchantApi merchantApi;
    private final OrderRepository orders;
    private final PaymentRepository payments;

    public SellerPaymentConfirmationService(CurrentUserApi currentUserApi, MerchantApi merchantApi, OrderRepository orders, PaymentRepository payments) {
        this.currentUserApi = currentUserApi;
        this.merchantApi = merchantApi;
        this.orders = orders;
        this.payments = payments;
    }

    // PAY 05:
    // Chủ shop xác nhận đã nhận tiền chuyển khoản mô phỏng.
    // Không tự động chuyển Order sang CONFIRMED.
    @Transactional
    public void confirmBankTransfer(String orderCode) {
        // 1. Xác định người dùng hiện tại.
        UserSummary actor = currentUserApi.requireCurrentUser();

        if (actor.userId() == null || actor.role() != UserRole.USER || actor.accountStatus() != AccountStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản không được phép xác nhận.");
        }

        // 2. Kiểm tra mã đơn.
        if (orderCode == null || orderCode.isBlank()) {
            throw notFound();
        }

        // 3. Tìm đơn hàng.
        Order order = orders.findByOrderCode(orderCode).orElseThrow(SellerPaymentConfirmationService::notFound);

        // 4. Kiểm tra đơn thuộc shop nào.
        MerchantShopSummary shop = merchantApi.findShopSummary(order.getShopId()).orElseThrow(SellerPaymentConfirmationService::notFound);

        // 5. Chỉ chủ shop ACTIVE được xác nhận.
        // Trả 404 để không tiết lộ đơn của shop khác.
        if (!shop.ownerActive() || !Objects.equals(shop.ownerId(), actor.userId())) {
            throw notFound();
        }

        // 6. Khóa đơn hàng trong transaction.
        // Chặn hai request xác nhận cùng một đơn đồng thời.
        Order lockedOrder = orders.findByIdForUpdate(order.getId()).orElseThrow(SellerPaymentConfirmationService::notFound);

        // 7. Chỉ xác nhận cho đơn PENDING.
        if (lockedOrder.getStatus() != OrderStatus.PENDING) {
            throw conflict("Chỉ xác nhận thanh toán cho đơn hàng đang chờ xác nhận.");
        }

        // 8. Đọc payment từ bảng payments.
        Payment payment = payments
                .findByOrderId(lockedOrder.getId())
                .orElseThrow(() -> conflict("Đơn hàng chưa có bản ghi thanh toán."));

        // 9. Không cho xác nhận COD.
        if (payment.getMethod() != PaymentMethod.BANK_TRANSFER) {
            throw conflict("Đơn hàng không sử dụng chuyển khoản mô phỏng.");
        }

        // 10. Chỉ PENDING mới được chuyển thành PAID.
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw conflict("Thanh toán không còn ở trạng thái chờ xác nhận.");
        }

        // 11. Đổi trạng thái và ghi paid_at.
        payment.markBankTransferPaid();

        // 12. Lưu payment trong cùng transaction.
        payments.save(payment);

        // Không thay đổi Order.status.
        // Việc xác nhận đơn là trách nhiệm của ORDER 04.
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đơn hàng.");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}