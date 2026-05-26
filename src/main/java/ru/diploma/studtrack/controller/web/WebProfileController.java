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

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class WebProfileController {

    private final UserService userService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final WebErrorMessageService webErrorMessageService;

    @GetMapping
    public String viewProfile(Model model) {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getFullName() == null || currentUser.getFullName().isBlank()) {
            currentUser.setFullName("User");
        }

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

    @PostMapping("/update")
    public String updateProfile(@RequestParam String fullName,
                                RedirectAttributes redirectAttributes) {
        return executeProfileAction(
                redirectAttributes,
                "Профиль обновлён",
                () -> {
                    User current = userService.getCurrentUser();
                    User updated = User.builder()
                            .fullName(fullName)
                            .build();
                    userService.update(current.getId(), updated);
                }
        );
    }

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
