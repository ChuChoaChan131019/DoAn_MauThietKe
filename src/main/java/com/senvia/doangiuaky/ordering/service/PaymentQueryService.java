package com.senvia.doangiuaky.ordering.service;

import com.senvia.doangiuaky.ordering.dto.PaymentDetails;
import com.senvia.doangiuaky.ordering.entity.Payment;
import com.senvia.doangiuaky.ordering.repository.PaymentRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class PaymentQueryService {
    private final PaymentRepository paymentRepository;

    public PaymentQueryService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    // ORDER 09: Helper nội bộ module Ordering.
    // Chỉ gọi sau khi service đã xác thực quyền trên order,
    // hoặc khi tạo receipt ngay trong transaction checkout.
    @Transactional(readOnly = true)
    PaymentDetails getByOrderId(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("orderId không hợp lệ.");
        }

        // PAY 07: Chỉ lấy phương thức và trạng thái
        // thanh toán từ bảng payments.
        Payment payment = paymentRepository
                .findByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy payment cho order " + orderId));

        return new PaymentDetails(
                payment.getOrderId(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getTransactionReference(),
                payment.getPaidAt(),
                payment.getRefundedAt()
        );
    }
}
