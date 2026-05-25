package ru.diploma.studtrack.controller.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.diploma.studtrack.service.NotificationService;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class WebNotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public String list(@RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
                       Model model) {
        model.addAttribute("notifications",
                unreadOnly ? notificationService.getUnreadForCurrentUser() : notificationService.getAllForCurrentUser());
        model.addAttribute("unreadOnly", unreadOnly);
        model.addAttribute("pageTitle", "Уведомления");
        return "notifications/list";
    }

    @GetMapping("/unread-count")
    public String unreadCount(Model model) {
        try {
            model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
        } catch (Exception e) {
            log.warn("Ошибка получения количества непрочитанных уведомлений: {}", e.getMessage());
            model.addAttribute("unreadCount", 0);
        }
        return "fragments/notifications :: unreadBadge";
    }

    @GetMapping("/dropdown")
    public String dropdown(Model model) {
        try {
            model.addAttribute("notifications", notificationService.getRecentForCurrentUser());
            model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
        } catch (Exception e) {
            log.warn("Ошибка загрузки dropdown уведомлений: {}", e.getMessage());
            model.addAttribute("notifications", java.util.List.of());
            model.addAttribute("unreadCount", 0);
            model.addAttribute("notificationErrorMessage", e.getMessage());
        }
        return "fragments/notifications :: dropdownList";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable UUID id, Model model) {
        try {
            notificationService.markAsRead(id);
            model.addAttribute("notifications", notificationService.getRecentForCurrentUser());
            model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
        } catch (Exception e) {
            log.warn("Ошибка отметки уведомления {} как прочитанного: {}", id, e.getMessage());
            model.addAttribute("notifications", java.util.List.of());
            model.addAttribute("unreadCount", 0);
            model.addAttribute("notificationErrorMessage", e.getMessage());
        }
        return "fragments/notifications :: dropdownList";
    }

    @PostMapping("/read-all")
    public String markAllRead(Model model) {
        try {
            notificationService.markAllAsReadForCurrentUser();
            model.addAttribute("notifications", notificationService.getRecentForCurrentUser());
            model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
        } catch (Exception e) {
            log.warn("Ошибка отметки всех уведомлений как прочитанных: {}", e.getMessage());
            model.addAttribute("notifications", java.util.List.of());
            model.addAttribute("unreadCount", 0);
            model.addAttribute("notificationErrorMessage", e.getMessage());
        }
        return "fragments/notifications :: dropdownList";
    }
}
