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
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class WebNotificationController {

    private final NotificationService notificationService;
    private final WebErrorMessageService webErrorMessageService;

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
        return executeDropdownOperation(model, "Ошибка загрузки dropdown уведомлений", () -> fillDropdownModel(model));
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable UUID id, Model model) {
        return executeDropdownOperation(
                model,
                "Ошибка отметки уведомления " + id + " как прочитанного",
                () -> {
                    notificationService.markAsRead(id);
                    fillDropdownModel(model);
                }
        );
    }

    @PostMapping("/read-all")
    public String markAllRead(Model model) {
        return executeDropdownOperation(
                model,
                "Ошибка отметки всех уведомлений как прочитанных",
                () -> {
                    notificationService.markAllAsReadForCurrentUser();
                    fillDropdownModel(model);
                }
        );
    }

    private void fillDropdownModel(Model model) {
        model.addAttribute("notifications", notificationService.getRecentForCurrentUser());
        model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
    }

    private void fillDropdownFallbackModel(Model model, String errorMessage) {
        model.addAttribute("notifications", java.util.List.of());
        model.addAttribute("unreadCount", 0);
        model.addAttribute("notificationErrorMessage", errorMessage);
    }

    private String executeDropdownOperation(Model model,
                                            String logPrefix,
                                            Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("{}: {}", logPrefix, e.getMessage());
            fillDropdownFallbackModel(
                    model,
                    webErrorMessageService.resolve(e, "Не удалось загрузить уведомления. Попробуйте обновить страницу.")
            );
        }
        return "fragments/notifications :: dropdownList";
    }
}
