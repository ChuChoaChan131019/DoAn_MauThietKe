package com.senvia.doangiuaky.engagement.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class NewsletterSignupForm {

    @NotBlank(message = "Vui lòng nhập địa chỉ email.")
    @Email(message = "Địa chỉ email không hợp lệ.")
    @Size(max = 254, message = "Địa chỉ email quá dài.")
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.strip();
    }
}
