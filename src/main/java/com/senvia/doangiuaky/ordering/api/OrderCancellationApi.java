package com.senvia.doangiuaky.ordering.api;

/** Lệnh hủy đơn dùng chung cho giao diện Buyer, Seller và Admin. */
public interface OrderCancellationApi {

    /**
     * Buyer đang đăng nhập hủy đơn của chính mình.
     *
     * @param orderCode mã đơn hàng
     * @param reason lý do hủy, sau khi bỏ khoảng trắng phải có 1–500 ký tự
     */
    void cancelAsBuyer(String orderCode, String reason);

    /**
     * Chủ shop đang đăng nhập hủy đơn thuộc gian hàng mình sở hữu.
     *
     * Shop bị khóa vẫn có thể xử lý đơn cũ nếu Merchant API cho phép.
     */
    void cancelAsSeller(String orderCode, String reason);

    /**
     * Admin đang hoạt động hủy đơn hợp lệ.
     */
    void cancelAsAdmin(String orderCode, String reason);
}
