package com.senvia.doangiuaky.ordering.dto;

import com.senvia.doangiuaky.ordering.entity.PaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CheckoutPaymentForm {
    @NotBlank(message = "Vui lòng nhập tên người nhận.")
    @Size(max = 100, message = "Tên không được vượt quá 100 ký tự.")
    private String receiverName;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự.")
    @Pattern(
        regexp = "^\\s*\\+?[0-9]{9,15}\\s*$",
        message = "Số điện thoại không hợp lệ."
    )
    private String receiverPhone;

    @NotBlank(message = "Vui lòng nhập địa chỉ nhận hàng.")
    @Size(max = 2000, message = "Địa chỉ quá dài.")
    private String shippingAddress;

    @Size(max = 2000, message = "Ghi chú quá dài.")
    private String note;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán.")
    private PaymentMethod paymentMethod;

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
