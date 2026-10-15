package com.senvia.doangiuaky.merchant.service;

/** The seller submitted a stock value based on a product snapshot that is no longer current. */
public class ProductStockConflictException extends RuntimeException {

    public ProductStockConflictException() {
        super("Tồn kho đã thay đổi. Vui lòng tải lại trang sản phẩm trước khi lưu.");
    }
}
