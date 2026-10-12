package com.senvia.doangiuaky.merchant.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class ProductForm {

    @NotBlank(message = "Tên sản phẩm không được để trống.")
    @Size(max = 200, message = "Tên sản phẩm không được vượt quá 200 ký tự.")
    private String name;

    @NotBlank(message = "Mô tả sản phẩm không được để trống.")
    private String description;

    @NotNull(message = "Vui lòng chọn danh mục.")
    @Positive(message = "Danh mục không hợp lệ.")
    private Long categoryId;

    @NotNull(message = "Giá sản phẩm không được để trống.")
    @Digits(integer = 10, fraction = 2, message = "Giá tối đa 10 chữ số nguyên và 2 chữ số thập phân.")
    @Positive(message = "Giá sản phẩm phải lớn hơn 0.")
    private BigDecimal price;

    @NotNull(message = "Tồn kho không được để trống.")
    @Min(value = 0, message = "Tồn kho không được âm.")
    private Integer stockQuantity;

    private List<MultipartFile> images = new ArrayList<>();

    private List<Long> removedImageIds = new ArrayList<>();

    public ProductForm() {
    }

    public ProductForm(String name, String description, Long categoryId,
                       BigDecimal price, Integer stockQuantity) {
        this.name = name;
        this.description = description;
        this.categoryId = categoryId;
        this.price = price;
        this.stockQuantity = stockQuantity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
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

    public List<MultipartFile> getImages() {
        return images;
    }

    public void setImages(List<MultipartFile> images) {
        this.images = images == null ? new ArrayList<>() : images;
    }

    public List<Long> getRemovedImageIds() {
        return removedImageIds;
    }

    public void setRemovedImageIds(List<Long> removedImageIds) {
        this.removedImageIds = removedImageIds == null ? new ArrayList<>() : removedImageIds;
    }
}
