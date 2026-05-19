package ru.diploma.studtrack.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.diploma.studtrack.dto.request.TaskCreateRequest;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class WebTaskController {

    private final TaskService taskService;
    private final TaskAssigneeService taskAssigneeService;
    private final TaskReviewerService taskReviewerService;
    private final CommentService commentService;
    private final UserService userService;
    private final ProjectService projectService;
    private final TaskReviewRoundService reviewRoundService;
    private final ChangeRequestService changeRequestService;
    private final TaskHistoryService taskHistoryService;
    private final TaskAttachmentService taskAttachmentService;

    @GetMapping("/my")
    public String myTasks(@RequestParam(required = false) Task.Priority priority,
                          @RequestParam(required = false) String deadline,
                          @RequestParam(required = false) UUID projectId,
                          Model model) {
        List<Task> assignedTasks = taskService.getAssignedToMe().stream()
                .filter(t -> t.getStatus() != Task.TaskStatus.DONE)
                .filter(t -> priority == null || t.getPriority() == priority)
                .filter(t -> {
                    if (deadline == null) return true;
                    LocalDate today = LocalDate.now();
                    return switch (deadline) {
                        case "overdue" -> t.getDeadline() != null && t.getDeadline().isBefore(today);
                        case "today" -> t.getDeadline() != null && t.getDeadline().isEqual(today);
                        case "week" -> t.getDeadline() != null && !t.getDeadline().isBefore(today) && !t.getDeadline().isAfter(today.plusDays(7));
                        default -> true;
                    };
                })
                .filter(t -> projectId == null || t.getProject().getId().equals(projectId))
                .toList();

        List<TaskReviewer> pendingReviews = taskService.getPendingReviewsForMe();

        model.addAttribute("assignedTasks", assignedTasks);
        model.addAttribute("pendingReviews", pendingReviews);
        model.addAttribute("priorities", Task.Priority.values());
        model.addAttribute("selectedPriority", priority);
        model.addAttribute("selectedDeadline", deadline);
        model.addAttribute("selectedProjectId", projectId);
        model.addAttribute("pageTitle", "Мои задачи");
        return "tasks/my";
    }

    @PostMapping("/projects/{projectId}/tasks")
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

        List<Task> tasks = taskService.getTasksByProject(projectId);
        Map<Task.TaskStatus, List<Task>> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus));
        Map<UUID, String> reviewStateByTaskId = taskService.getReviewStateByTaskId(tasks);
        Map<UUID, TaskService.ReviewStats> reviewStatsByTaskId = taskService.getReviewStatsByTaskId(tasks);

        model.addAttribute("project", projectService.findById(projectId));
        model.addAttribute("tasksByStatus", tasksByStatus);
        model.addAttribute("reviewStateByTaskId", reviewStateByTaskId);
        model.addAttribute("reviewStatsByTaskId", reviewStatsByTaskId);
        model.addAttribute("statuses", Task.TaskStatus.values());
        model.addAttribute("priorities", Task.Priority.values());

        return "projects/fragments :: kanbanBoard";
    }

    @GetMapping("/{id}")
    public String viewTask(@PathVariable UUID id, Model model) {
        Task task = taskService.findById(id);
        UUID projectId = task.getProject().getId();
        projectService.checkMembership(projectId);

        User currentUser = userService.getCurrentUser();
        boolean isOwner = projectService.isOwner(projectId, currentUser.getId());

        List<TaskReviewer> reviewers = taskReviewerService.getReviewersByTask(id);
        List<TaskAssignee> assignees = taskAssigneeService.getAssigneesByTask(id);
        List<Comment> comments = commentService.getByTask(id);
        List<TaskReviewRound> reviewRounds = reviewRoundService.getRoundsForView(id);
        List<TaskHistory> historyEntries = taskHistoryService.getByTask(id);
        List<TaskAttachment> attachments = taskAttachmentService.getAttachments(id);
        Map<UUID, String> historyMessageById = new LinkedHashMap<>();
        historyEntries.forEach(entry -> historyMessageById.put(entry.getId(), taskHistoryService.toHumanMessage(entry)));
        List<User> projectMembers = projectService.getMembers(projectId)
                .stream()
                .map(pm -> pm.getUser())
                .toList();

        boolean isCurrentUserReviewer = reviewers.stream()
                .anyMatch(r -> r.getUser().getId().equals(currentUser.getId()));
        boolean isCurrentUserAssignee = taskAssigneeService.isAssignee(id, currentUser.getId());
        boolean canStartNewRound = isCurrentUserReviewer && reviewRoundService.canStartNewRound(id);
        boolean lastRoundCompleted = reviewRoundService.isLastRoundCompleted(id);

        model.addAttribute("task", task);
        model.addAttribute("reviewers", reviewers);
        model.addAttribute("assignees", assignees);
        model.addAttribute("comments", comments);
        model.addAttribute("reviewRounds", reviewRounds);
        model.addAttribute("historyEntries", historyEntries);
        model.addAttribute("historyMessageById", historyMessageById);
        model.addAttribute("attachments", attachments);
        model.addAttribute("projectMembers", projectMembers);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("isCurrentUserReviewer", isCurrentUserReviewer);
        model.addAttribute("isCurrentUserAssignee", isCurrentUserAssignee);
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("canStartNewRound", canStartNewRound);
        model.addAttribute("lastRoundCompleted", lastRoundCompleted);
        model.addAttribute("pageTitle", "Задача: " + task.getTitle());
        model.addAttribute("statuses", Task.TaskStatus.values());
        model.addAttribute("priorities", Task.Priority.values());
        model.addAttribute("reviewStatuses", TaskReviewer.ReviewStatus.values());

        return "tasks/detail";
    }

    @GetMapping("/{id}/history")
    public String getTaskHistory(@PathVariable UUID id, Model model) {
        Task task = taskService.findById(id);
        projectService.checkMembership(task.getProject().getId());

        List<TaskHistory> historyEntries = taskHistoryService.getByTask(id);
        Map<UUID, String> historyMessageById = new LinkedHashMap<>();
        historyEntries.forEach(entry -> historyMessageById.put(entry.getId(), taskHistoryService.toHumanMessage(entry)));

        model.addAttribute("historyEntries", historyEntries);
        model.addAttribute("historyMessageById", historyMessageById);
        return "fragments/task-history :: historyList";
    }

    @PostMapping("/{taskId}/assignees")
    public String addAssignee(@PathVariable UUID taskId,
                              @RequestParam UUID assigneeId,
                              Model model) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        taskAssigneeService.addAssignee(taskId, assigneeId);
        fillAssigneeListModel(model, taskId);
        return "fragments/task-assignees :: assigneeList";
    }

    @DeleteMapping("/{taskId}/assignees/{assigneeId}")
    public String removeAssignee(@PathVariable UUID taskId,
                                 @PathVariable UUID assigneeId,
                                 Model model) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        taskAssigneeService.removeAssignee(taskId, assigneeId);
        fillAssigneeListModel(model, taskId);
        return "fragments/task-assignees :: assigneeList";
    }

    @PostMapping("/{id}/assign")
    public String assignTask(@PathVariable UUID id,
                             @RequestParam(required = false) UUID assigneeId,
                             RedirectAttributes redirectAttributes) {
        try {
            Task task = taskService.findById(id);
            UUID projectId = task.getProject().getId();
            projectService.checkMembership(projectId);

            UUID currentUserId = userService.getCurrentUserId();
            boolean isOwner = projectService.isOwner(projectId, currentUserId);
            if (isOwner && assigneeId != null) {
                taskAssigneeService.addAssignee(id, assigneeId);
            } else if (!isOwner) {
                taskAssigneeService.addAssignee(id, currentUserId);
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "Укажите исполнителя для назначения");
            }
        } catch (Exception e) {
            log.warn("Ошибка назначения исполнителя для задачи {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable UUID id,
                               @RequestParam Task.TaskStatus status,
                               @RequestParam(required = false, defaultValue = "detail") String returnTo,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        try {
            Task task = taskService.changeStatus(id, status);
            UUID projectId = task.getProject().getId();

            if ("kanban".equals(returnTo)) {
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

            return "redirect:/tasks/" + id;
        } catch (Exception e) {
            log.warn("Ошибка смены статуса задачи {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/tasks/" + id;
        }
    }

    @PostMapping("/{id}/update")
    public String updateTask(@PathVariable UUID id,
                             @RequestParam String title,
                             @RequestParam(required = false) String description,
                             @RequestParam Task.Priority priority,
                             @RequestParam(required = false) UUID assigneeId,
                             @RequestParam(required = false, defaultValue = "false") boolean reviewRequired,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
                             RedirectAttributes redirectAttributes) {
        try {
            taskService.update(id, title, description, priority, assigneeId, reviewRequired, deadline);
        } catch (Exception e) {
            log.warn("Ошибка обновления задачи {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + id;
    }

    @PostMapping("/{taskId}/rounds/{roundId}/complete")
    public String completeReviewRound(@PathVariable UUID taskId,
                                      @PathVariable UUID roundId,
                                      RedirectAttributes redirectAttributes) {
        try {
            reviewRoundService.completeRound(roundId);
        } catch (Exception e) {
            log.warn("Ошибка завершения раунда {}: {}", roundId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/{taskId}/rounds")
    public String createReviewRound(@PathVariable UUID taskId,
                                    @RequestParam(required = false) String summaryComment,
                                    RedirectAttributes redirectAttributes) {
        if (!reviewRoundService.canStartNewRound(taskId)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Нельзя начать новый раунд: в предыдущем раунде есть незакрытые замечания.");
            return "redirect:/tasks/" + taskId;
        }
        try {
            reviewRoundService.createNewRound(taskId, summaryComment);
        } catch (Exception e) {
            log.warn("Ошибка создания раунда ревью для задачи {}: {}", taskId, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/{taskId}/rounds/{roundId}/change-requests")
    public String addChangeRequest(@PathVariable UUID taskId,
                                   @PathVariable UUID roundId,
                                   @RequestParam String content,
                                   RedirectAttributes redirectAttributes) {
        try {
            changeRequestService.create(taskId, roundId, content);
        } catch (Exception e) {
            log.warn("Ошибка добавления замечания: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/change-requests/{id}/resolve")
    public String resolveChangeRequest(@PathVariable UUID id,
                                       @RequestParam UUID taskId,
                                       RedirectAttributes redirectAttributes) {
        try {
            changeRequestService.markAsResolved(id);
        } catch (Exception e) {
            log.warn("Ошибка закрытия замечания {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/change-requests/{id}/reopen")
    public String reopenChangeRequest(@PathVariable UUID id,
                                      @RequestParam UUID taskId,
                                      RedirectAttributes redirectAttributes) {
        try {
            changeRequestService.markAsOpen(id);
        } catch (Exception e) {
            log.warn("Ошибка переоткрытия замечания {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/change-requests/{id}/reject")
    public String rejectChangeRequest(@PathVariable UUID id,
                                      @RequestParam UUID taskId,
                                      RedirectAttributes redirectAttributes) {
        try {
            changeRequestService.markAsRejected(id);
        } catch (Exception e) {
            log.warn("Ошибка отклонения замечания {}: {}", id, e.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/tasks/" + taskId;
    }

    @PostMapping("/{taskId}/reviewers")
    public String addReviewer(@PathVariable UUID taskId,
                              @RequestParam UUID reviewerId,
                              Model model) {
        var task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        taskReviewerService.addReviewer(taskId, reviewerId);

        var reviewers = taskReviewerService.getReviewersByTask(taskId);
        fillReviewerListModel(model, task, reviewers);
        return "fragments/task-reviewers :: reviewerList";
    }

    @DeleteMapping("/{taskId}/reviewers/{reviewerId}")
    public String removeReviewer(@PathVariable UUID taskId,
                                 @PathVariable UUID reviewerId,
                                 Model model) {
        var task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        taskReviewerService.removeReviewer(taskId, reviewerId);

        var reviewers = taskReviewerService.getReviewersByTask(taskId);
        fillReviewerListModel(model, task, reviewers);
        return "fragments/task-reviewers :: reviewerList";
    }

    @PostMapping("/{taskId}/reviewers/{reviewerId}/submit")
    public String submitReview(@PathVariable UUID taskId,
                               @PathVariable UUID reviewerId,
                               @RequestParam TaskReviewer.ReviewStatus status,
                               @RequestParam(required = false) String comment,
                               Model model) {
        var task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        taskReviewerService.submitReview(taskId, reviewerId, status, comment);

        var reviewers = taskReviewerService.getReviewersByTask(taskId);
        fillReviewerListModel(model, task, reviewers);
        return "fragments/task-reviewers :: reviewerList";
    }

    private void fillReviewerListModel(Model model, Task task, List<TaskReviewer> reviewers) {
        User currentUser = userService.getCurrentUser();
        model.addAttribute("taskId", task.getId());
        model.addAttribute("reviewers", reviewers);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("isCurrentUserAssignee", taskAssigneeService.isAssignee(task.getId(), currentUser.getId()));
        model.addAttribute("isOwner", projectService.isOwner(task.getProject().getId(), currentUser.getId()));
        model.addAttribute("lastRoundCompleted", reviewRoundService.isLastRoundCompleted(task.getId()));
    }

    private void fillAssigneeListModel(Model model, UUID taskId) {
        Task task = taskService.findById(taskId);
        User currentUser = userService.getCurrentUser();
        model.addAttribute("taskId", taskId);
        model.addAttribute("assignees", taskAssigneeService.getAssigneesByTask(taskId));
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("isOwner", projectService.isOwner(task.getProject().getId(), currentUser.getId()));
        model.addAttribute("isCurrentUserAssignee", taskAssigneeService.isAssignee(taskId, currentUser.getId()));
    }
}
