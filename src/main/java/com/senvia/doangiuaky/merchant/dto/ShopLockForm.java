package com.senvia.doangiuaky.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Form used by an Admin to record why an approved shop is locked. */
public class ShopLockForm {

    @NotBlank(message = "Vui lòng nhập lý do khóa gian hàng.")
    @Size(max = 500, message = "Lý do khóa không được vượt quá 500 ký tự.")
    private String reason;

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
