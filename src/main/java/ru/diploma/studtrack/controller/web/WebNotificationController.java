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
/**
 * Обрабатывает веб-интерфейс уведомлений пользователя.
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class WebNotificationController {

    /**
     * Предоставляет операции чтения и изменения уведомлений.
     */
    private final NotificationService notificationService;
    /**
     * Преобразует исключения в пользовательские сообщения для UI.
     */
    private final WebErrorMessageService webErrorMessageService;

    /**
     * Отображает страницу уведомлений с фильтром по непрочитанным.
     *
     * @param unreadOnly флаг показа только непрочитанных уведомлений
     * @param model модель представления
     * @return имя шаблона страницы уведомлений
     */
    @GetMapping
    public String list(@RequestParam(name = "unreadOnly", defaultValue = "false") boolean unreadOnly,
                       Model model) {
        model.addAttribute("notifications",
                unreadOnly ? notificationService.getUnreadForCurrentUser() : notificationService.getAllForCurrentUser());
        model.addAttribute("unreadOnly", unreadOnly);
        model.addAttribute("pageTitle", "Уведомления");
        return "notifications/list";
    }

    /**
     * Возвращает фрагмент с количеством непрочитанных уведомлений.
     *
     * @param model модель представления
     * @return имя фрагмента бейджа уведомлений
     */
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

    /**
     * Возвращает фрагмент выпадающего списка уведомлений.
     *
     * @param model модель представления
     * @return имя фрагмента списка уведомлений
     */
    @GetMapping("/dropdown")
    public String dropdown(Model model) {
        return executeDropdownOperation(model, "Ошибка загрузки dropdown уведомлений", () -> fillDropdownModel(model));
    }

    /**
     * Отмечает уведомление как прочитанное и возвращает обновлённый dropdown.
     *
     * @param id идентификатор уведомления
     * @param model модель представления
     * @return имя фрагмента списка уведомлений
     */
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

    /**
     * Отмечает все уведомления пользователя как прочитанные.
     *
     * @param model модель представления
     * @return имя фрагмента списка уведомлений
     */
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

    /**
     * Заполняет модель данными для dropdown уведомлений.
     *
     * @param model модель представления
     */
    private void fillDropdownModel(Model model) {
        model.addAttribute("notifications", notificationService.getRecentForCurrentUser());
        model.addAttribute("unreadCount", notificationService.getUnreadCountForCurrentUser());
    }

    /**
     * Заполняет fallback-данные для dropdown при ошибке.
     *
     * @param model модель представления
     * @param errorMessage текст ошибки для пользователя
     */
    private void fillDropdownFallbackModel(Model model, String errorMessage) {
        model.addAttribute("notifications", java.util.List.of());
        model.addAttribute("unreadCount", 0);
        model.addAttribute("notificationErrorMessage", errorMessage);
    }

    /**
     * Выполняет действие над dropdown уведомлений с единым обработчиком ошибок.
     *
     * @param model модель представления
     * @param logPrefix префикс сообщения в логах
     * @param action операция над уведомлениями
     * @return имя фрагмента списка уведомлений
     */
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
