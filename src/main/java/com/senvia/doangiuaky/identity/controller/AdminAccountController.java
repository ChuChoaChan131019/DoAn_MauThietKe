package com.senvia.doangiuaky.identity.controller;

import com.senvia.doangiuaky.identity.api.AccountStatus;
import com.senvia.doangiuaky.identity.dto.AdminAccountView;
import com.senvia.doangiuaky.identity.security.IdentityPrincipal;
import com.senvia.doangiuaky.identity.service.AccountOperationException;
import com.senvia.doangiuaky.identity.service.AdminAccountService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;

@Controller
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @GetMapping("/identity/admin/accounts")
    public String accounts(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "status", required = false) AccountStatus status,
            @AuthenticationPrincipal IdentityPrincipal principal,
            Model model) {
        model.addAttribute("accounts", adminAccountService.search(query, status));
        model.addAttribute("query", query);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", AccountStatus.values());
        model.addAttribute("currentAdminId", principal.getUserId());
        return "identity/admin/accounts";
    }

    @GetMapping("/identity/admin/accounts/{id}")
    public String account(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal IdentityPrincipal principal,
            Model model) {
        try {
            return addSelectedAccount(id, principal.getUserId(), model);
        } catch (AccountOperationException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    @PostMapping("/identity/admin/accounts/{id}/lock")
    public String lock(
            @PathVariable("id") Long id,
            @RequestParam(name = "reason", required = false) String reason,
            @AuthenticationPrincipal IdentityPrincipal principal,
            RedirectAttributes redirectAttributes) {
        try {
            adminAccountService.lock(id, principal.getUserId(), reason);
            redirectAttributes.addFlashAttribute("success", "Tài khoản đã được khóa.");
        } catch (AccountOperationException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/identity/admin/accounts/" + id;
    }

    @PostMapping("/identity/admin/accounts/{id}/unlock")
    public String unlock(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal IdentityPrincipal principal,
            RedirectAttributes redirectAttributes) {
        try {
            adminAccountService.unlock(id, principal.getUserId());
            redirectAttributes.addFlashAttribute("success", "Tài khoản đã được mở khóa.");
        } catch (AccountOperationException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/identity/admin/accounts/" + id;
    }

    private String addSelectedAccount(Long id, Long currentAdminId, Model model) {
        AdminAccountView account = adminAccountService.getAccount(id);
        model.addAttribute("selectedAccount", account);
        model.addAttribute("accounts", adminAccountService.search(null, null));
        model.addAttribute("statuses", AccountStatus.values());
        model.addAttribute("currentAdminId", currentAdminId);
        return "identity/admin/accounts";
    }
}
