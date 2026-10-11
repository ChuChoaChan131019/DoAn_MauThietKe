package com.senvia.doangiuaky.merchant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CategoryForm {

    @NotBlank(message = "Tên danh mục không được để trống.")
    @Size(max = 100, message = "Tên danh mục không được vượt quá 100 ký tự.")
    private String name;

    private String description;

    public CategoryForm() {
    }

    public CategoryForm(String name, String description) {
        this.name = name;
        this.description = description;
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
}
