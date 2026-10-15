package com.senvia.doangiuaky.identity.controller;

import com.senvia.doangiuaky.identity.dto.RegistrationForm;
import com.senvia.doangiuaky.identity.service.DuplicateEmailException;
import com.senvia.doangiuaky.identity.service.IdentityService;
import com.senvia.doangiuaky.identity.service.InvalidPasswordException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.Objects;

@Controller
public class IdentityController {

    private final IdentityService identityService;

    public IdentityController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @GetMapping("/login")
    public String login() {
        return "identity/login";
    }

    @GetMapping("/register")
    public String register(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
        return "identity/register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registrationForm") RegistrationForm form,
            BindingResult bindingResult) {
        if (!Objects.equals(form.getPassword(), form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Mật khẩu xác nhận không khớp.");
        }
        if (bindingResult.hasErrors()) {
            return "identity/register";
        }

        try {
            identityService.register(form);
        } catch (DuplicateEmailException exception) {
            bindingResult.rejectValue("email", "email.duplicate", exception.getMessage());
            return "identity/register";
        } catch (InvalidPasswordException exception) {
            bindingResult.rejectValue("password", "password.invalid", exception.getMessage());
            return "identity/register";
        }
        return "redirect:/login?registered";
    }
}
