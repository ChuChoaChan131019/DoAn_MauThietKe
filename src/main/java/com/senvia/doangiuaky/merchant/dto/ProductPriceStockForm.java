package com.senvia.doangiuaky.merchant.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Reusable input contract for product price and stock fields.
 * Product create/edit flows add their own product-specific fields around this contract.
 */
public class ProductPriceStockForm {

    @NotNull(message = "Giá sản phẩm là bắt buộc.")
    @DecimalMin(value = "0.00", inclusive = false,
            message = "Giá sản phẩm phải lớn hơn 0.")
    @Digits(integer = 10, fraction = 2,
            message = "Giá sản phẩm tối đa 10 chữ số phần nguyên và 2 chữ số thập phân.")
    private BigDecimal price;

    @NotNull(message = "Tồn kho là bắt buộc.")
    @Min(value = 0, message = "Tồn kho không được âm.")
    private Integer stockQuantity;

    public ProductPriceStockForm() {
    }

    public ProductPriceStockForm(BigDecimal price, Integer stockQuantity) {
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity) {
        this.stockQuantity = stockQuantity;
    }
}
