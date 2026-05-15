package ru.diploma.studtrack.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.diploma.studtrack.dto.request.TaskCreateRequest;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.UserService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class WebProjectController {

    private final ProjectService projectService;
    private final TaskService taskService;
    private final UserService userService;

    @GetMapping
    public String listProjects(Model model) {
        List<Project> projects = projectService.getMyProjects();
        model.addAttribute("projects", projects);
        model.addAttribute("pageTitle", "Мои проекты");
        return "projects/list";
    }

    @GetMapping("/create")
    public String createProjectForm(Model model) {
        model.addAttribute("pageTitle", "Создать проект");
        return "projects/create";
    }

    @PostMapping("/create")
    public String createProject(@RequestParam String name,
                                @RequestParam(required = false) String description) {
        log.info("Создание проекта: name={}", name);
        Project project = projectService.create(name, description);
        return "redirect:/projects/" + project.getId();
    }

    @GetMapping("/{id}")
    public String viewProject(@PathVariable UUID id,
                              @RequestParam(required = false, defaultValue = "board") String tab,
                              Model model) {
        Project project = projectService.findById(id);
        projectService.checkMembership(id);

        List<Task> tasks = taskService.getTasksByProject(id);
        Map<Task.TaskStatus, List<Task>> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus));
        Map<UUID, String> reviewStateByTaskId = taskService.getReviewStateByTaskId(tasks);
        Map<UUID, TaskService.ReviewStats> reviewStatsByTaskId = taskService.getReviewStatsByTaskId(tasks);
        List<ProjectMember> members = projectService.getMembers(id);
        User currentUser = userService.getCurrentUser();
        boolean isOwner = projectService.isOwner(id, currentUser.getId());

        model.addAttribute("project", project);
        model.addAttribute("tasksByStatus", tasksByStatus);
        model.addAttribute("reviewStateByTaskId", reviewStateByTaskId);
        model.addAttribute("reviewStatsByTaskId", reviewStatsByTaskId);
        model.addAttribute("statuses", Task.TaskStatus.values());
        model.addAttribute("priorities", Task.Priority.values());
        model.addAttribute("members", members);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("activeTab", tab);
        model.addAttribute("pageTitle", project.getName());
        return "projects/detail";
    }

    @GetMapping("/{id}/board")
    public String getKanbanBoard(@PathVariable UUID id, Model model) {
        Project project = projectService.findById(id);
        projectService.checkMembership(id);

        List<Task> tasks = taskService.getTasksByProject(id);
        Map<Task.TaskStatus, List<Task>> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus));
        Map<UUID, String> reviewStateByTaskId = taskService.getReviewStateByTaskId(tasks);
        Map<UUID, TaskService.ReviewStats> reviewStatsByTaskId = taskService.getReviewStatsByTaskId(tasks);

        model.addAttribute("project", project);
        model.addAttribute("tasksByStatus", tasksByStatus);
        model.addAttribute("reviewStateByTaskId", reviewStateByTaskId);
        model.addAttribute("reviewStatsByTaskId", reviewStatsByTaskId);
        model.addAttribute("statuses", Task.TaskStatus.values());
        return "projects/fragments :: kanbanBoard";
    }

    @PostMapping("/{projectId}/tasks")
    public String createTask(@PathVariable UUID projectId,
                             @Valid @ModelAttribute TaskCreateRequest request,
                             Model model) {
        projectService.checkMembership(projectId);

        taskService.create(
                projectId,
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.isReviewRequired(),
                request.getAssigneeId(),
                request.getDeadline()
        );

        Project project = projectService.findById(projectId);
        List<Task> tasks = taskService.getTasksByProject(projectId);
        Map<Task.TaskStatus, List<Task>> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus));
        Map<UUID, String> reviewStateByTaskId = taskService.getReviewStateByTaskId(tasks);
        Map<UUID, TaskService.ReviewStats> reviewStatsByTaskId = taskService.getReviewStatsByTaskId(tasks);

        model.addAttribute("project", project);
        model.addAttribute("tasksByStatus", tasksByStatus);
        model.addAttribute("reviewStateByTaskId", reviewStateByTaskId);
        model.addAttribute("reviewStatsByTaskId", reviewStatsByTaskId);
        model.addAttribute("statuses", Task.TaskStatus.values());

        return "projects/fragments :: kanbanBoard";
    }

    @PostMapping("/{id}/update")
    public String updateProject(@PathVariable UUID id,
                                @RequestParam String name,
                                @RequestParam(required = false) String description,
                                RedirectAttributes redirectAttributes) {
        try {
            projectService.update(id, name, description);
        } catch (Exception e) {
            log.warn("Ошибка обновления проекта {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/projects/" + id + "?tab=settings";
    }

    @PostMapping("/{id}/delete")
    public String deleteProject(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            projectService.delete(id);
        } catch (Exception e) {
            log.warn("Ошибка удаления проекта {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/projects/" + id + "?tab=settings";
        }
        return "redirect:/projects";
    }

    @PostMapping("/{id}/members/add")
    public String addMember(@PathVariable UUID id,
                            @RequestParam String email,
                            RedirectAttributes redirectAttributes) {
        try {
            User user = userService.findByEmail(email);
            projectService.addMember(id, user.getId());
        } catch (Exception e) {
            log.warn("Ошибка добавления участника в проект {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/projects/" + id + "?tab=members";
    }

    @PostMapping("/{id}/members/{userId}/remove")
    public String removeMember(@PathVariable UUID id,
                               @PathVariable UUID userId,
                               RedirectAttributes redirectAttributes) {
        try {
            projectService.removeMember(id, userId);
        } catch (Exception e) {
            log.warn("Ошибка удаления участника из проекта {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/projects/" + id + "?tab=members";
    }

    @PostMapping("/{id}/leave")
    public String leaveProject(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        try {
            projectService.leaveProject(id);
        } catch (Exception e) {
            log.warn("Ошибка при выходе из проекта {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/projects/" + id + "?tab=members";
        }
        return "redirect:/projects";
    }
}
