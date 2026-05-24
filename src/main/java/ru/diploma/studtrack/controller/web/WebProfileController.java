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

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
public class WebProfileController {

    private final UserService userService;
    private final ProjectService projectService;
    private final TaskService taskService;

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
        try {
            User current = userService.getCurrentUser();
            User updated = User.builder()
                    .fullName(fullName)
                    .build();
            userService.update(current.getId(), updated);
            redirectAttributes.addFlashAttribute("successMessage", "Профиль обновлён");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/change-password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 RedirectAttributes redirectAttributes) {
        try {
            UUID currentUserId = userService.getCurrentUserId();
            userService.changePassword(currentUserId, oldPassword, newPassword);
            redirectAttributes.addFlashAttribute("successMessage", "Пароль изменён");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/avatar")
    public String updateAvatar(@RequestParam("avatar") MultipartFile avatar,
                               RedirectAttributes redirectAttributes) {
        try {
            UUID currentUserId = userService.getCurrentUserId();
            userService.updateAvatar(currentUserId, avatar);
            redirectAttributes.addFlashAttribute("successMessage", "Аватар обновлён");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }

    @PostMapping("/avatar/delete")
    public String deleteAvatar(RedirectAttributes redirectAttributes) {
        try {
            UUID currentUserId = userService.getCurrentUserId();
            userService.deleteAvatar(currentUserId);
            redirectAttributes.addFlashAttribute("successMessage", "Аватар удалён");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/profile";
    }
}
