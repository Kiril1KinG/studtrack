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
/**
 * Обрабатывает веб-операции по задачам: просмотр, изменение, назначение и ревью.
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class WebTaskController {

    /**
     * Предоставляет основные операции управления задачами.
     */
    private final TaskService taskService;
    /**
     * Предоставляет операции по исполнителям задачи.
     */
    private final TaskAssigneeService taskAssigneeService;
    /**
     * Предоставляет операции по ревьюерам задачи.
     */
    private final TaskReviewerService taskReviewerService;
    /**
     * Предоставляет операции с комментариями задачи.
     */
    private final CommentService commentService;
    /**
     * Предоставляет данные текущего пользователя.
     */
    private final UserService userService;
    /**
     * Проверяет членство пользователя в проекте.
     */
    private final ProjectService projectService;
    /**
     * Предоставляет операции с раундами ревью.
     */
    private final TaskReviewRoundService reviewRoundService;
    /**
     * Предоставляет операции с замечаниями ревью.
     */
    private final ChangeRequestService changeRequestService;
    /**
     * Предоставляет историю изменений задачи.
     */
    private final TaskHistoryService taskHistoryService;
    /**
     * Предоставляет операции с вложениями задачи.
     */
    private final TaskAttachmentService taskAttachmentService;
    /**
     * Преобразует исключения в пользовательские сообщения для UI.
     */
    private final WebErrorMessageService webErrorMessageService;

    /**
     * Отображает страницу «Мои задачи» с фильтрацией по приоритету, сроку и проекту.
     *
     * @param priority фильтр по приоритету
     * @param deadline фильтр по сроку
     * @param projectId фильтр по проекту
     * @param model модель представления
     * @return имя шаблона страницы «Мои задачи»
     */
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

    /**
     * Создаёт задачу в проекте и возвращает обновлённый фрагмент канбан-доски.
     *
     * @param projectId идентификатор проекта
     * @param request данные создания задачи
     * @param model модель представления
     * @return имя фрагмента канбан-доски
     */
    @PostMapping("/projects/{projectId}/tasks")
    public String createTask(@PathVariable UUID projectId,
                             @Valid @ModelAttribute TaskCreateRequest request,
                             Model model) {
        getAccessibleProject(projectId);

        taskService.create(
                projectId,
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.isReviewRequired(),
                request.getAssigneeId(),
                request.getDeadline()
        );
        addKanbanBoardAttributes(model, projectId);
        model.addAttribute("priorities", Task.Priority.values());

        return "projects/fragments :: kanbanBoard";
    }

    /**
     * Отображает детальную страницу задачи со всеми связанными данными.
     *
     * @param id идентификатор задачи
     * @param model модель представления
     * @return имя шаблона детальной страницы задачи
     */
    @GetMapping("/{id}")
    public String viewTask(@PathVariable UUID id, Model model) {
        Task task = getAccessibleTask(id);
        UUID projectId = task.getProject().getId();

        User currentUser = userService.getCurrentUser();
        boolean isOwner = projectService.isOwner(projectId, currentUser.getId());

        List<TaskReviewer> reviewers = taskReviewerService.getReviewersByTask(id);
        List<TaskAssignee> assignees = taskAssigneeService.getAssigneesByTask(id);
        List<Comment> comments = commentService.getByTask(id);
        List<TaskReviewRound> reviewRounds = reviewRoundService.getRoundsForView(id);
        List<TaskHistory> historyEntries = taskHistoryService.getByTask(id);
        List<TaskAttachment> attachments = taskAttachmentService.getTaskArtifacts(id);
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

    /**
     * Возвращает HTMX-фрагмент истории изменений задачи.
     *
     * @param id идентификатор задачи
     * @param model модель представления
     * @return имя фрагмента истории задачи
     */
    @GetMapping("/{id}/history")
    public String getTaskHistory(@PathVariable UUID id, Model model) {
        Task task = getAccessibleTask(id);

        List<TaskHistory> historyEntries = taskHistoryService.getByTask(id);
        Map<UUID, String> historyMessageById = new LinkedHashMap<>();
        historyEntries.forEach(entry -> historyMessageById.put(entry.getId(), taskHistoryService.toHumanMessage(entry)));

        model.addAttribute("historyEntries", historyEntries);
        model.addAttribute("historyMessageById", historyMessageById);
        return "fragments/task-history :: historyList";
    }

    /**
     * Добавляет исполнителя в задачу и возвращает обновлённый список исполнителей.
     *
     * @param taskId идентификатор задачи
     * @param assigneeId идентификатор исполнителя
     * @param model модель представления
     * @return имя фрагмента списка исполнителей
     */
    @PostMapping("/{taskId}/assignees")
    public String addAssignee(@PathVariable UUID taskId,
                              @RequestParam UUID assigneeId,
                              Model model) {
        executeAssigneeAction(
                taskId,
                model,
                () -> taskAssigneeService.addAssignee(taskId, assigneeId),
                "добавления исполнителя",
                "Не удалось добавить исполнителя. Попробуйте еще раз."
        );
        fillAssigneeListModel(model, taskId);
        return "fragments/task-assignees :: assigneeList";
    }

    /**
     * Удаляет исполнителя из задачи и возвращает обновлённый список исполнителей.
     *
     * @param taskId идентификатор задачи
     * @param assigneeId идентификатор исполнителя
     * @param model модель представления
     * @return имя фрагмента списка исполнителей
     */
    @DeleteMapping("/{taskId}/assignees/{assigneeId}")
    public String removeAssignee(@PathVariable UUID taskId,
                                 @PathVariable UUID assigneeId,
                                 Model model) {
        executeAssigneeAction(
                taskId,
                model,
                () -> taskAssigneeService.removeAssignee(taskId, assigneeId),
                "удаления исполнителя",
                "Не удалось удалить исполнителя. Попробуйте еще раз."
        );
        fillAssigneeListModel(model, taskId);
        return "fragments/task-assignees :: assigneeList";
    }

    /**
     * Назначает исполнителя на задачу с учётом роли владельца проекта.
     *
     * @param id идентификатор задачи
     * @param assigneeId идентификатор исполнителя
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу задачи
     */
    @PostMapping("/{id}/assign")
    public String assignTask(@PathVariable UUID id,
                             @RequestParam(required = false) UUID assigneeId,
                             RedirectAttributes redirectAttributes) {
        return executeTaskAction(
                id,
                "назначения исполнителя",
                redirectAttributes,
                () -> {
                    Task task = getAccessibleTask(id);
                    UUID projectId = task.getProject().getId();
                    UUID currentUserId = userService.getCurrentUserId();
                    boolean isOwner = projectService.isOwner(projectId, currentUserId);
                    if (isOwner && assigneeId != null) {
                        taskAssigneeService.addAssignee(id, assigneeId);
                    } else if (!isOwner) {
                        taskAssigneeService.addAssignee(id, currentUserId);
                    } else {
                        redirectAttributes.addFlashAttribute("errorMessage", "Укажите исполнителя для назначения");
                    }
                }
        );
    }

    /**
     * Изменяет статус задачи с поддержкой возврата в детальную страницу или канбан.
     *
     * @param id идентификатор задачи
     * @param status новый статус
     * @param returnTo целевая точка возврата
     * @param redirectAttributes атрибуты flash-сообщений
     * @param model модель представления
     * @return имя фрагмента или URL перенаправления
     */
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
                addKanbanBoardAttributes(model, projectId);
                return "projects/fragments :: kanbanBoard";
            }

            return redirectToTask(id);
        } catch (Exception e) {
            log.warn("Ошибка смены статуса задачи {}: {}", id, e.getMessage());
            addRedirectError(redirectAttributes, e, "Не удалось изменить статус задачи. Попробуйте еще раз.");
            return redirectToTask(id);
        }
    }

    /**
     * Обновляет параметры задачи.
     *
     * @param id идентификатор задачи
     * @param title заголовок задачи
     * @param description описание задачи
     * @param priority приоритет задачи
     * @param assigneeId идентификатор исполнителя
     * @param reviewRequired флаг обязательного ревью
     * @param deadline срок выполнения
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу задачи
     */
    @PostMapping("/{id}/update")
    public String updateTask(@PathVariable UUID id,
                             @RequestParam String title,
                             @RequestParam(required = false) String description,
                             @RequestParam Task.Priority priority,
                             @RequestParam(required = false) UUID assigneeId,
                             @RequestParam(required = false, defaultValue = "false") boolean reviewRequired,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deadline,
                             RedirectAttributes redirectAttributes) {
        return executeTaskAction(
                id,
                "обновления",
                redirectAttributes,
                () -> taskService.update(id, title, description, priority, assigneeId, reviewRequired, deadline)
        );
    }

    /**
     * Завершает раунд ревью задачи.
     *
     * @param taskId идентификатор задачи
     * @param roundId идентификатор раунда ревью
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу задачи
     */
    @PostMapping("/{taskId}/rounds/{roundId}/complete")
    public String completeReviewRound(@PathVariable UUID taskId,
                                      @PathVariable UUID roundId,
                                      RedirectAttributes redirectAttributes) {
        return executeTaskAction(
                taskId,
                "завершения раунда",
                redirectAttributes,
                () -> reviewRoundService.completeRound(roundId)
        );
    }

    /**
     * Создаёт новый раунд ревью для задачи.
     *
     * @param taskId идентификатор задачи
     * @param summaryComment итоговый комментарий
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу задачи
     */
    @PostMapping("/{taskId}/rounds")
    public String createReviewRound(@PathVariable UUID taskId,
                                    @RequestParam(required = false) String summaryComment,
                                    RedirectAttributes redirectAttributes) {
        if (!reviewRoundService.canStartNewRound(taskId)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Нельзя начать новый раунд: в предыдущем раунде есть незакрытые замечания.");
            return redirectToTask(taskId);
        }
        return executeTaskAction(
                taskId,
                "создания раунда ревью",
                redirectAttributes,
                () -> reviewRoundService.createNewRound(taskId, summaryComment)
        );
    }

    /**
     * Добавляет замечание в раунд ревью задачи.
     *
     * @param taskId идентификатор задачи
     * @param roundId идентификатор раунда ревью
     * @param content текст замечания
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу задачи
     */
    @PostMapping("/{taskId}/rounds/{roundId}/change-requests")
    public String addChangeRequest(@PathVariable UUID taskId,
                                   @PathVariable UUID roundId,
                                   @RequestParam String content,
                                   RedirectAttributes redirectAttributes) {
        return executeTaskAction(
                taskId,
                "добавления замечания",
                redirectAttributes,
                () -> changeRequestService.create(taskId, roundId, content)
        );
    }

    /**
     * Отмечает замечание как исправленное.
     *
     * @param id идентификатор замечания
     * @param taskId идентификатор задачи
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу задачи
     */
    @PostMapping("/change-requests/{id}/resolve")
    public String resolveChangeRequest(@PathVariable UUID id,
                                       @RequestParam UUID taskId,
                                       RedirectAttributes redirectAttributes) {
        return executeTaskAction(
                taskId,
                "закрытия замечания",
                redirectAttributes,
                () -> changeRequestService.markAsResolved(id)
        );
    }

    /**
     * Переоткрывает замечание.
     *
     * @param id идентификатор замечания
     * @param taskId идентификатор задачи
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу задачи
     */
    @PostMapping("/change-requests/{id}/reopen")
    public String reopenChangeRequest(@PathVariable UUID id,
                                      @RequestParam UUID taskId,
                                      RedirectAttributes redirectAttributes) {
        return executeTaskAction(
                taskId,
                "переоткрытия замечания",
                redirectAttributes,
                () -> changeRequestService.markAsOpen(id)
        );
    }

    /**
     * Отклоняет замечание.
     *
     * @param id идентификатор замечания
     * @param taskId идентификатор задачи
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления на страницу задачи
     */
    @PostMapping("/change-requests/{id}/reject")
    public String rejectChangeRequest(@PathVariable UUID id,
                                      @RequestParam UUID taskId,
                                      RedirectAttributes redirectAttributes) {
        return executeTaskAction(
                taskId,
                "отклонения замечания",
                redirectAttributes,
                () -> changeRequestService.markAsRejected(id)
        );
    }

    /**
     * Добавляет ревьюера к задаче и возвращает обновлённый список ревьюеров.
     *
     * @param taskId идентификатор задачи
     * @param reviewerId идентификатор ревьюера
     * @param model модель представления
     * @return имя фрагмента списка ревьюеров
     */
    @PostMapping("/{taskId}/reviewers")
    public String addReviewer(@PathVariable UUID taskId,
                              @RequestParam UUID reviewerId,
                              Model model) {
        Task task = executeReviewerAction(
                taskId,
                model,
                () -> taskReviewerService.addReviewer(taskId, reviewerId),
                "добавления ревьюера",
                "Не удалось добавить ревьюера. Попробуйте еще раз."
        );
        List<TaskReviewer> reviewers = taskReviewerService.getReviewersByTask(taskId);
        fillReviewerListModel(model, task, reviewers);
        return "fragments/task-reviewers :: reviewerList";
    }

    /**
     * Удаляет ревьюера из задачи и возвращает обновлённый список ревьюеров.
     *
     * @param taskId идентификатор задачи
     * @param reviewerId идентификатор ревьюера
     * @param model модель представления
     * @return имя фрагмента списка ревьюеров
     */
    @DeleteMapping("/{taskId}/reviewers/{reviewerId}")
    public String removeReviewer(@PathVariable UUID taskId,
                                 @PathVariable UUID reviewerId,
                                 Model model) {
        Task task = executeReviewerAction(
                taskId,
                model,
                () -> taskReviewerService.removeReviewer(taskId, reviewerId),
                "удаления ревьюера",
                "Не удалось удалить ревьюера. Попробуйте еще раз."
        );
        List<TaskReviewer> reviewers = taskReviewerService.getReviewersByTask(taskId);
        fillReviewerListModel(model, task, reviewers);
        return "fragments/task-reviewers :: reviewerList";
    }

    /**
     * Отправляет результат ревью и возвращает обновлённый список ревьюеров.
     *
     * @param taskId идентификатор задачи
     * @param reviewerId идентификатор ревьюера
     * @param status статус ревью
     * @param comment комментарий ревью
     * @param model модель представления
     * @return имя фрагмента списка ревьюеров
     */
    @PostMapping("/{taskId}/reviewers/{reviewerId}/submit")
    public String submitReview(@PathVariable UUID taskId,
                               @PathVariable UUID reviewerId,
                               @RequestParam TaskReviewer.ReviewStatus status,
                               @RequestParam(required = false) String comment,
                               Model model) {
        Task task = executeReviewerAction(
                taskId,
                model,
                () -> taskReviewerService.submitReview(taskId, reviewerId, status, comment),
                "отправки ревью",
                "Не удалось отправить ревью. Попробуйте еще раз."
        );
        List<TaskReviewer> reviewers = taskReviewerService.getReviewersByTask(taskId);
        fillReviewerListModel(model, task, reviewers);
        return "fragments/task-reviewers :: reviewerList";
    }

    /**
     * Заполняет модель атрибутами списка ревьюеров.
     *
     * @param model модель представления
     * @param task задача
     * @param reviewers список ревьюеров
     */
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

    /**
     * Заполняет модель атрибутами списка исполнителей.
     *
     * @param model модель представления
     * @param taskId идентификатор задачи
     */
    private void fillAssigneeListModel(Model model, UUID taskId) {
        Task task = getAccessibleTask(taskId);
        User currentUser = userService.getCurrentUser();
        model.addAttribute("taskId", taskId);
        model.addAttribute("assignees", taskAssigneeService.getAssigneesByTask(taskId));
        model.addAttribute("currentUserId", currentUser.getId());
        model.addAttribute("isOwner", projectService.isOwner(task.getProject().getId(), currentUser.getId()));
        model.addAttribute("isCurrentUserAssignee", taskAssigneeService.isAssignee(taskId, currentUser.getId()));
    }

    /**
     * Возвращает проект и проверяет доступ пользователя по членству.
     *
     * @param projectId идентификатор проекта
     * @return доступный проект
     */
    private Project getAccessibleProject(UUID projectId) {
        Project project = projectService.findById(projectId);
        projectService.checkMembership(projectId);
        return project;
    }

    /**
     * Возвращает задачу и проверяет доступ пользователя по членству в проекте.
     *
     * @param taskId идентификатор задачи
     * @return доступная задача
     */
    private Task getAccessibleTask(UUID taskId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        return task;
    }

    /**
     * Выполняет действие над задачей с единым обработчиком ошибок и редиректом.
     *
     * @param taskId идентификатор задачи
     * @param actionLabel название действия для логирования
     * @param redirectAttributes атрибуты flash-сообщений
     * @param action выполняемое действие
     * @return URL перенаправления на страницу задачи
     */
    private String executeTaskAction(UUID taskId,
                                     String actionLabel,
                                     RedirectAttributes redirectAttributes,
                                     Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Ошибка {} для задачи {}: {}", actionLabel, taskId, e.getMessage());
            addRedirectError(redirectAttributes, e, "Не удалось выполнить действие для задачи. Попробуйте еще раз.");
        }
        return redirectToTask(taskId);
    }

    /**
     * Выполняет действие с исполнителем и добавляет ошибку в модель при исключении.
     *
     * @param taskId идентификатор задачи
     * @param model модель представления
     * @param action выполняемое действие
     * @param actionLabel название действия для логирования
     * @param fallbackMessage fallback-сообщение для пользователя
     */
    private void executeAssigneeAction(UUID taskId,
                                       Model model,
                                       Runnable action,
                                       String actionLabel,
                                       String fallbackMessage) {
        try {
            getAccessibleTask(taskId);
            action.run();
        } catch (Exception e) {
            log.warn("Ошибка {} в задаче {}: {}", actionLabel, taskId, e.getMessage());
            model.addAttribute("assigneeErrorMessage", webErrorMessageService.resolve(e, fallbackMessage));
        }
    }

    /**
     * Выполняет действие с ревьюером и добавляет ошибку в модель при исключении.
     *
     * @param taskId идентификатор задачи
     * @param model модель представления
     * @param action выполняемое действие
     * @param actionLabel название действия для логирования
     * @param fallbackMessage fallback-сообщение для пользователя
     * @return задача после проверки доступа
     */
    private Task executeReviewerAction(UUID taskId,
                                       Model model,
                                       Runnable action,
                                       String actionLabel,
                                       String fallbackMessage) {
        Task task = getAccessibleTask(taskId);
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Ошибка {} в задаче {}: {}", actionLabel, taskId, e.getMessage());
            model.addAttribute("reviewerErrorMessage", webErrorMessageService.resolve(e, fallbackMessage));
        }
        return task;
    }

    /**
     * Добавляет сообщение об ошибке в redirect-атрибуты.
     *
     * @param redirectAttributes атрибуты flash-сообщений
     * @param exception исключение
     * @param fallbackMessage fallback-сообщение для пользователя
     */
    private void addRedirectError(RedirectAttributes redirectAttributes,
                                  Exception exception,
                                  String fallbackMessage) {
        redirectAttributes.addFlashAttribute("errorMessage", webErrorMessageService.resolve(exception, fallbackMessage));
    }

    /**
     * Формирует URL редиректа на детальную страницу задачи.
     *
     * @param taskId идентификатор задачи
     * @return URL редиректа
     */
    private String redirectToTask(UUID taskId) {
        return "redirect:/tasks/" + taskId;
    }

    /**
     * Заполняет модель атрибутами канбан-доски проекта.
     *
     * @param model модель представления
     * @param projectId идентификатор проекта
     */
    private void addKanbanBoardAttributes(Model model, UUID projectId) {
        Project project = getAccessibleProject(projectId);
        List<Task> tasks = taskService.getTasksByProject(projectId);
        KanbanModel kanbanModel = buildKanbanModel(tasks);
        model.addAttribute("project", project);
        model.addAttribute("tasksByStatus", kanbanModel.tasksByStatus());
        model.addAttribute("reviewStateByTaskId", kanbanModel.reviewStateByTaskId());
        model.addAttribute("reviewStatsByTaskId", kanbanModel.reviewStatsByTaskId());
        model.addAttribute("statuses", Task.TaskStatus.values());
    }

    /**
     * Строит модель канбан-доски из списка задач проекта.
     *
     * @param tasks задачи проекта
     * @return агрегированная модель канбан-доски
     */
    private KanbanModel buildKanbanModel(List<Task> tasks) {
        Map<Task.TaskStatus, List<Task>> tasksByStatus = tasks.stream()
                .collect(Collectors.groupingBy(Task::getStatus));
        Map<UUID, String> reviewStateByTaskId = taskService.getReviewStateByTaskId(tasks);
        Map<UUID, TaskService.ReviewStats> reviewStatsByTaskId = taskService.getReviewStatsByTaskId(tasks);
        return new KanbanModel(tasksByStatus, reviewStateByTaskId, reviewStatsByTaskId);
    }

    /**
     * Хранит агрегированные данные для отображения канбан-доски.
     *
     * @param tasksByStatus задачи по статусам
     * @param reviewStateByTaskId текстовый статус ревью по задачам
     * @param reviewStatsByTaskId числовая статистика ревью по задачам
     */
    private record KanbanModel(
            Map<Task.TaskStatus, List<Task>> tasksByStatus,
            Map<UUID, String> reviewStateByTaskId,
            Map<UUID, TaskService.ReviewStats> reviewStatsByTaskId
    ) {
    }
}
