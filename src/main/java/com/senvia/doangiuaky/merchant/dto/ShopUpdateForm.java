package com.senvia.doangiuaky.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import com.senvia.doangiuaky.merchant.entity.Shop;

public class ShopUpdateForm {

    @NotBlank(message = "Vui lòng nhập tên gian hàng.")
    @Size(max = 150, message = "Tên gian hàng không được vượt quá 150 ký tự.")
    private String shopName;

    @NotBlank(message = "Vui lòng nhập mô tả gian hàng.")
    @Size(max = 2000, message = "Mô tả gian hàng không được vượt quá 2000 ký tự.")
    private String description;

    private MultipartFile logo;

    private boolean removeLogo;

    @NotBlank(message = "Vui lòng nhập số điện thoại hỗ trợ.")
    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự.")
    @Pattern(regexp = "^[0-9+() .-]{8,20}$", message = "Số điện thoại không đúng định dạng.")
    private String phone;

    @NotBlank(message = "Vui lòng nhập địa chỉ cửa hàng hoặc kho hàng.")
    @Size(max = 2000, message = "Địa chỉ không được vượt quá 2000 ký tự.")
    private String address;

    public static ShopUpdateForm from(Shop shop) {
        ShopUpdateForm form = new ShopUpdateForm();
        form.shopName = shop.getShopName();
        form.description = shop.getDescription();
        form.phone = shop.getPhone();
        form.address = shop.getAddress();
        return form;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public MultipartFile getLogo() {
        return logo;
    }

    public void setLogo(MultipartFile logo) {
        this.logo = logo;
    }

    public boolean isRemoveLogo() {
        return removeLogo;
    }

    public void setRemoveLogo(boolean removeLogo) {
        this.removeLogo = removeLogo;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
