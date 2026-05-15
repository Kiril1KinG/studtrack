package ru.diploma.studtrack.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import ru.diploma.studtrack.service.NotificationService;

import java.util.UUID;

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
        model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
        return "fragments/notifications :: unreadBadge";
    }

    @GetMapping("/dropdown")
    public String dropdown(Model model) {
        model.addAttribute("notifications", notificationService.getRecentForCurrentUser());
        model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
        return "fragments/notifications :: dropdownList";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable UUID id, Model model) {
        notificationService.markAsRead(id);
        model.addAttribute("notifications", notificationService.getRecentForCurrentUser());
        model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
        return "fragments/notifications :: dropdownList";
    }

    @PostMapping("/read-all")
    public String markAllRead(Model model) {
        notificationService.markAllAsReadForCurrentUser();
        model.addAttribute("notifications", notificationService.getRecentForCurrentUser());
        model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
        return "fragments/notifications :: dropdownList";
    }
}
