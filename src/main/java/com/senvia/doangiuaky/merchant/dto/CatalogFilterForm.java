package com.senvia.doangiuaky.merchant.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Form values accepted by the public catalog filter. */
public class CatalogFilterForm {

    @Size(max = 200, message = "Từ khóa tìm kiếm không được vượt quá 200 ký tự.")
    private String q;

    @DecimalMin(value = "0.00", message = "Giá phải lớn hơn hoặc bằng 0.")
    @Digits(integer = 10, fraction = 2,
            message = "Giá tối đa có 10 chữ số phần nguyên và 2 chữ số thập phân.")
    private BigDecimal min;

    @DecimalMin(value = "0.00", message = "Giá phải lớn hơn hoặc bằng 0.")
    @Digits(integer = 10, fraction = 2,
            message = "Giá tối đa có 10 chữ số phần nguyên và 2 chữ số thập phân.")
    private BigDecimal max;

    @Positive(message = "Danh mục không hợp lệ.")
    private Long categoryId;

    private List<@NotNull @Positive(message = "Danh mục không hợp lệ.") Long> categoryIds = new ArrayList<>();

    /** Kept for links created before the catalog switched to category IDs. */
    private String category;

    private List<String> shop = new ArrayList<>();

    @AssertTrue(message = "Giá tối thiểu không được lớn hơn giá tối đa.")
    public boolean isPriceRangeValid() {
        return min == null || max == null || min.compareTo(max) <= 0;
    }

    public String getQ() {
        return q;
    }

    public void setQ(String q) {
        this.q = q;
    }

    public BigDecimal getMin() {
        return min;
    }

    public void setMin(BigDecimal min) {
        this.min = min;
    }

    public BigDecimal getMax() {
        return max;
    }

    public void setMax(BigDecimal max) {
        this.max = max;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public List<Long> getCategoryIds() {
        return categoryIds;
    }

    public void setCategoryIds(List<Long> categoryIds) {
        this.categoryIds = categoryIds == null ? new ArrayList<>() : categoryIds;
    }

    public List<Long> getRequestedCategoryIds() {
        return !categoryIds.isEmpty() ? categoryIds.stream().distinct().toList()
                : categoryId == null ? List.of() : List.of(categoryId);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getShop() {
        return shop;
    }

    public void setShop(List<String> shop) {
        this.shop = shop == null ? new ArrayList<>() : new ArrayList<>(shop);
    }
}
