package ru.diploma.studtrack.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.UserService;
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.List;
import java.util.UUID;

/**
 * Обрабатывает веб-страницы профиля пользователя.
 */
@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class WebProfileController {

    /**
     * Предоставляет операции управления пользователем и профилем.
     */
    private final UserService userService;
    /**
     * Предоставляет операции с проектами пользователя.
     */
    private final ProjectService projectService;
    /**
     * Предоставляет операции с задачами пользователя.
     */
    private final TaskService taskService;
    /**
     * Преобразует исключения в пользовательские сообщения для UI.
     */
    private final WebErrorMessageService webErrorMessageService;

    /**
     * Отображает страницу профиля текущего пользователя.
     *
     * @param model модель представления
     * @return имя шаблона страницы профиля
     */
    @GetMapping
    public String viewProfile(Model model) {
        User currentUser = userService.getCurrentUser();

        List<Task> assignedTasks = taskService.getAssignedToMe().stream()
                .filter(t -> t.getStatus() != Task.TaskStatus.DONE)
                .toList();

        model.addAttribute("user", currentUser);
        model.addAttribute("avatarUrl", userService.getAvatarUrl(currentUser));
        model.addAttribute("projects", projectService.getMyProjects());
        model.addAttribute("assignedTasks", assignedTasks);
        model.addAttribute("pageTitle", "Профиль");
        return "profile/view";
    }

    /**
     * Обновляет ФИО пользователя.
     *
     * @param lastName фамилия
     * @param firstName имя
     * @param patronymic отчество
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу профиля
     */
    @PostMapping("/update")
    public String updateProfile(@RequestParam String lastName,
                                @RequestParam String firstName,
                                @RequestParam(required = false) String patronymic,
                                RedirectAttributes redirectAttributes) {
        return executeProfileAction(
                redirectAttributes,
                "Профиль обновлён",
                () -> {
                    User current = userService.getCurrentUser();
                    User updated = User.builder()
                            .lastName(lastName)
                            .firstName(firstName)
                            .patronymic(patronymic)
                            .build();
                    userService.update(current.getId(), updated);
                }
        );
    }

    /**
     * Изменяет пароль текущего пользователя.
     *
     * @param oldPassword текущий пароль
     * @param newPassword новый пароль
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу профиля
     */
    @PostMapping("/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 RedirectAttributes redirectAttributes) {
        return executeProfileAction(
                redirectAttributes,
                "Пароль изменён",
                () -> {
                    UUID currentUserId = userService.getCurrentUserId();
                    userService.changePassword(currentUserId, oldPassword, newPassword);
                }
        );
    }

    /**
     * Обновляет аватар текущего пользователя.
     *
     * @param avatar загруженный файл аватара
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу профиля
     */
    @PostMapping("/avatar")
    public String updateAvatar(@RequestParam("avatar") MultipartFile avatar,
                               RedirectAttributes redirectAttributes) {
        return executeProfileAction(
                redirectAttributes,
                "Аватар обновлён",
                () -> {
                    UUID currentUserId = userService.getCurrentUserId();
                    userService.updateAvatar(currentUserId, avatar);
                }
        );
    }

    /**
     * Удаляет аватар текущего пользователя.
     *
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу профиля
     */
    @PostMapping("/avatar/delete")
    public String deleteAvatar(RedirectAttributes redirectAttributes) {
        return executeProfileAction(
                redirectAttributes,
                "Аватар удалён",
                () -> {
                    UUID currentUserId = userService.getCurrentUserId();
                    userService.deleteAvatar(currentUserId);
                }
        );
    }

    /**
     * Выполняет действие профиля с единым шаблоном обработки ошибок.
     *
     * @param redirectAttributes атрибуты flash-сообщений
     * @param successMessage сообщение об успехе
     * @param action выполняемое действие
     * @return URL перенаправления на страницу профиля
     */
    private String executeProfileAction(RedirectAttributes redirectAttributes,
                                        String successMessage,
                                        Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    webErrorMessageService.resolve(e, "Не удалось выполнить действие в профиле. Попробуйте еще раз.")
            );
        }
        return "redirect:/profile";
    }
}
