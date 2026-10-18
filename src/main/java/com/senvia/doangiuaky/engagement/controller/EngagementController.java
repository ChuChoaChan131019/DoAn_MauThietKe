package com.senvia.doangiuaky.engagement.controller;

import com.senvia.doangiuaky.engagement.dto.NotificationPageView;
import com.senvia.doangiuaky.engagement.service.NotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class EngagementController {

    private final NotificationService notificationService;

    public EngagementController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/account/notifications")
    public String notifications(@RequestParam(defaultValue = "1") int page, Model model) {
        NotificationPageView notificationPage = notificationService.getNotifications(page);
        model.addAttribute("notificationPage", notificationPage);
        model.addAttribute("notifications", notificationPage.items());
        model.addAttribute("unreadNotifications", notificationPage.unreadCount());
        return "engagement/notifications";
    }

    @PostMapping("/account/notifications/{id}/read")
    public String markRead(@PathVariable Long id,
                           @RequestParam(defaultValue = "1") int page,
                           RedirectAttributes redirectAttributes) {
        boolean changed = notificationService.markRead(id);
        redirectAttributes.addFlashAttribute("successMessage", changed
                ? "Đã đánh dấu thông báo là đã đọc."
                : "Thông báo này đã được đánh dấu là đã đọc.");
        return redirectToPage(page);
    }

    @PostMapping("/account/notifications/read-all")
    public String markAllRead(@RequestParam(defaultValue = "1") int page,
                              RedirectAttributes redirectAttributes) {
        int changed = notificationService.markAllRead();
        redirectAttributes.addFlashAttribute("successMessage", changed > 0
                ? "Đã đánh dấu tất cả thông báo là đã đọc."
                : "Bạn không có thông báo chưa đọc.");
        return redirectToPage(page);
    }

    private String redirectToPage(int page) {
        int normalizedPage = Math.max(page, 1);
        return "redirect:/account/notifications?page=" + normalizedPage;
    }

}
