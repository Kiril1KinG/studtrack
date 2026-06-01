package ru.diploma.studtrack.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.diploma.studtrack.dto.request.TaskCreateRequest;
import ru.diploma.studtrack.dto.request.ProjectStatisticsFilter;
import ru.diploma.studtrack.dto.response.ProjectStatisticsResponse;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.AttachmentHistoryValueService;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskAttachmentService;
import ru.diploma.studtrack.service.TaskHistoryService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.UserService;
import ru.diploma.studtrack.service.ProjectStatisticsService;
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
/**
 * Обрабатывает веб-страницы проекта: доску, репозиторий, статистику и участников.
 */
@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class WebProjectController {

    /**
     * Предоставляет операции управления проектами и участниками.
     */
    private final ProjectService projectService;
    /**
     * Предоставляет операции управления задачами проекта.
     */
    private final TaskService taskService;
    /**
     * Предоставляет данные текущего пользователя.
     */
    private final UserService userService;
    /**
     * Предоставляет операции со вложениями задач.
     */
    private final TaskAttachmentService taskAttachmentService;
    /**
     * Сохраняет историю изменений задач.
     */
    private final TaskHistoryService taskHistoryService;
    /**
     * Рассчитывает агрегированную статистику проекта.
     */
    private final ProjectStatisticsService projectStatisticsService;
    /**
     * Формирует читаемое значение вложения для истории задач.
     */
    private final AttachmentHistoryValueService attachmentHistoryValueService;
    /**
     * Преобразует исключения в пользовательские сообщения для UI.
     */
    private final WebErrorMessageService webErrorMessageService;

    /**
     * Отображает список проектов текущего пользователя.
     *
     * @param model модель представления
     * @return имя шаблона списка проектов
     */
    @GetMapping
    public String listProjects(Model model) {
        List<Project> projects = projectService.getMyProjects();
        model.addAttribute("projects", projects);
        model.addAttribute("pageTitle", "Мои проекты");
        return "projects/list";
    }

    /**
     * Отображает страницу создания проекта.
     *
     * @param model модель представления
     * @return имя шаблона страницы создания проекта
     */
    @GetMapping("/create")
    public String createProjectForm(Model model) {
        model.addAttribute("pageTitle", "Создать проект");
        return "projects/create";
    }

    /**
     * Создаёт новый проект и перенаправляет на его страницу.
     *
     * @param name название проекта
     * @param description описание проекта
     * @return URL перенаправления на страницу проекта
     */
    @PostMapping("/create")
    public String createProject(@RequestParam String name,
                                @RequestParam(required = false) String description) {
        log.info("Создание проекта: name={}", name);
        Project project = projectService.create(name, description);
        return "redirect:/projects/" + project.getId();
    }

    /**
     * Отображает страницу проекта с выбранной вкладкой и подготовленными данными.
     *
     * @param id идентификатор проекта
     * @param tab активная вкладка
     * @param sort сортировка артефактов репозитория
     * @param period период статистики
     * @param memberId фильтр статистики по участнику
     * @param model модель представления
     * @return имя шаблона страницы проекта
     */
    @GetMapping("/{id}")
    public String viewProject(@PathVariable UUID id,
                              @RequestParam(required = false, defaultValue = "board") String tab,
                              @RequestParam(required = false, defaultValue = "newest") String sort,
                              @RequestParam(required = false, defaultValue = "all") String period,
                              @RequestParam(required = false) UUID memberId,
                              Model model) {
        Project project = getAccessibleProject(id);
        List<Task> tasks = taskService.getTasksByProject(id);
        KanbanModel kanbanModel = buildKanbanModel(tasks);
        List<ProjectMember> members = projectService.getMembers(id);
        String repositorySort = sort;
        List<TaskAttachment> repositoryArtifacts = taskAttachmentService.getProjectArtifacts(id, repositorySort);
        ProjectStatisticsFilter statisticsFilter = ProjectStatisticsFilter.of(period, memberId);
        ProjectStatisticsResponse statistics = projectStatisticsService.getProjectStatistics(id, statisticsFilter);
        User currentUser = userService.getCurrentUser();
        boolean isOwner = projectService.isOwner(id, currentUser.getId());

        model.addAttribute("project", project);
        model.addAttribute("tasksByStatus", kanbanModel.tasksByStatus());
        model.addAttribute("reviewStateByTaskId", kanbanModel.reviewStateByTaskId());
        model.addAttribute("reviewStatsByTaskId", kanbanModel.reviewStatsByTaskId());
        model.addAttribute("statuses", Task.TaskStatus.values());
        model.addAttribute("priorities", Task.Priority.values());
        model.addAttribute("members", members);
        model.addAttribute("repositoryArtifacts", repositoryArtifacts);
        model.addAttribute("repositorySort", repositorySort);
        model.addAttribute("statistics", statistics);
        model.addAttribute("statisticsPeriod", statisticsFilter.period().code());
        model.addAttribute("statisticsMemberId", memberId);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("activeTab", tab);
        model.addAttribute("pageTitle", project.getName());
        return "projects/detail";
    }

    /**
     * Возвращает HTMX-фрагмент канбан-доски проекта.
     *
     * @param id идентификатор проекта
     * @param model модель представления
     * @return имя фрагмента канбан-доски
     */
    @GetMapping("/{id}/board")
    public String getKanbanBoard(@PathVariable UUID id, Model model) {
        Project project = getAccessibleProject(id);
        List<Task> tasks = taskService.getTasksByProject(id);
        addKanbanAttributes(model, project, tasks);
        return "projects/fragments :: kanbanBoard";
    }

    /**
     * Возвращает HTMX-фрагмент вкладки репозитория проекта.
     *
     * @param id идентификатор проекта
     * @param sort способ сортировки артефактов
     * @param model модель представления
     * @return имя фрагмента вкладки репозитория
     */
    @GetMapping("/{id}/repository")
    public String getRepository(@PathVariable UUID id,
                                @RequestParam(required = false, defaultValue = "newest") String sort,
                                Model model) {
        Project project = getAccessibleProject(id);

        List<TaskAttachment> artifacts = taskAttachmentService.getProjectArtifacts(id, sort);
        User currentUser = userService.getCurrentUser();
        boolean isOwner = projectService.isOwner(id, currentUser.getId());
        model.addAttribute("project", project);
        model.addAttribute("repositoryArtifacts", artifacts);
        model.addAttribute("repositorySort", sort);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("isOwner", isOwner);
        return "projects/fragments :: repositoryTab";
    }

    /**
     * Возвращает HTMX-фрагмент вкладки статистики проекта.
     *
     * @param id идентификатор проекта
     * @param period период расчёта статистики
     * @param memberId фильтр по участнику
     * @param model модель представления
     * @return имя фрагмента вкладки статистики
     */
    @GetMapping("/{id}/statistics")
    public String getStatistics(@PathVariable UUID id,
                                @RequestParam(required = false, defaultValue = "all") String period,
                                @RequestParam(required = false) UUID memberId,
                                Model model) {
        Project project = getAccessibleProject(id);
        List<ProjectMember> members = projectService.getMembers(id);
        ProjectStatisticsFilter filter = ProjectStatisticsFilter.of(period, memberId);
        ProjectStatisticsResponse statistics = projectStatisticsService.getProjectStatistics(id, filter);
        model.addAttribute("project", project);
        model.addAttribute("members", members);
        model.addAttribute("statistics", statistics);
        model.addAttribute("statisticsPeriod", filter.period().code());
        model.addAttribute("statisticsMemberId", memberId);
        return "projects/fragments :: statisticsTab";
    }

    /**
     * Удаляет артефакт из репозитория проекта и фиксирует событие в истории задачи.
     *
     * @param projectId идентификатор проекта
     * @param artifactId идентификатор артефакта
     * @param sort способ сортировки репозитория
     * @return URL перенаправления на вкладку репозитория
     */
    @PostMapping("/{projectId}/repository/{artifactId}/delete")
    public String deleteRepositoryArtifact(@PathVariable UUID projectId,
                                           @PathVariable UUID artifactId,
                                           @RequestParam(required = false, defaultValue = "newest") String sort) {
        projectService.checkMembership(projectId);
        TaskAttachment artifact = taskAttachmentService.findById(artifactId);
        taskHistoryService.recordFieldChange(
                artifact.getTask(),
                userService.getCurrentUser(),
                "attachments",
                attachmentHistoryValueService.historyValueFor(artifact),
                null
        );
        taskAttachmentService.deleteAttachment(artifactId);
        return "redirect:/projects/" + projectId + "?tab=repository&sort=" + sort;
    }

    /**
     * Создаёт задачу проекта и возвращает обновлённый фрагмент канбан-доски.
     *
     * @param projectId идентификатор проекта
     * @param request данные создания задачи
     * @param model модель представления
     * @return имя фрагмента канбан-доски
     */
    @PostMapping("/{projectId}/tasks")
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

        addProjectBoardModel(model, projectId);

        return "projects/fragments :: kanbanBoard";
    }

    /**
     * Обновляет проект и перенаправляет на вкладку настроек.
     *
     * @param id идентификатор проекта
     * @param name новое название
     * @param description новое описание
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления после операции
     */
    @PostMapping("/{id}/update")
    public String updateProject(@PathVariable UUID id,
                                @RequestParam String name,
                                @RequestParam(required = false) String description,
                                RedirectAttributes redirectAttributes) {
        return executeProjectAction(
                id,
                "обновления",
                redirectAttributes,
                redirectToTab(id, "settings"),
                redirectToTab(id, "settings"),
                () -> projectService.update(id, name, description)
        );
    }

    /**
     * Удаляет проект и перенаправляет на список проектов.
     *
     * @param id идентификатор проекта
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления после операции
     */
    @PostMapping("/{id}/delete")
    public String deleteProject(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return executeProjectAction(
                id,
                "удаления",
                redirectAttributes,
                "redirect:/projects",
                redirectToTab(id, "settings"),
                () -> projectService.delete(id)
        );
    }

    /**
     * Добавляет участника по email в проект.
     *
     * @param id идентификатор проекта
     * @param email email участника
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления после операции
     */
    @PostMapping("/{id}/members/add")
    public String addMember(@PathVariable UUID id,
                            @RequestParam String email,
                            RedirectAttributes redirectAttributes) {
        return executeProjectAction(
                id,
                "добавления участника",
                redirectAttributes,
                redirectToTab(id, "members"),
                redirectToTab(id, "members"),
                () -> {
                    User user = userService.findByEmail(email);
                    projectService.addMember(id, user.getId());
                }
        );
    }

    /**
     * Удаляет участника из проекта.
     *
     * @param id идентификатор проекта
     * @param userId идентификатор пользователя
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления после операции
     */
    @PostMapping("/{id}/members/{userId}/remove")
    public String removeMember(@PathVariable UUID id,
                               @PathVariable UUID userId,
                               RedirectAttributes redirectAttributes) {
        return executeProjectAction(
                id,
                "удаления участника",
                redirectAttributes,
                redirectToTab(id, "members"),
                redirectToTab(id, "members"),
                () -> projectService.removeMember(id, userId)
        );
    }

    /**
     * Выполняет выход текущего пользователя из проекта.
     *
     * @param id идентификатор проекта
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления после операции
     */
    @PostMapping("/{id}/leave")
    public String leaveProject(@PathVariable UUID id, RedirectAttributes redirectAttributes) {
        return executeProjectAction(
                id,
                "выхода из проекта",
                redirectAttributes,
                "redirect:/projects",
                redirectToTab(id, "members"),
                () -> projectService.leaveProject(id)
        );
    }

    /**
     * Возвращает проект и проверяет доступ текущего пользователя по членству.
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
     * Заполняет модель атрибутами канбан-доски проекта.
     *
     * @param model модель представления
     * @param project проект
     * @param tasks задачи проекта
     */
    private void addKanbanAttributes(Model model, Project project, List<Task> tasks) {
        KanbanModel kanbanModel = buildKanbanModel(tasks);
        model.addAttribute("project", project);
        model.addAttribute("tasksByStatus", kanbanModel.tasksByStatus());
        model.addAttribute("reviewStateByTaskId", kanbanModel.reviewStateByTaskId());
        model.addAttribute("reviewStatsByTaskId", kanbanModel.reviewStatsByTaskId());
        model.addAttribute("statuses", Task.TaskStatus.values());
    }

    /**
     * Перезаполняет модель данными доски проекта для ререндера HTMX-фрагмента.
     *
     * @param model модель представления
     * @param projectId идентификатор проекта
     */
    private void addProjectBoardModel(Model model, UUID projectId) {
        Project project = getAccessibleProject(projectId);
        List<Task> tasks = taskService.getTasksByProject(projectId);
        addKanbanAttributes(model, project, tasks);
    }

    /**
     * Выполняет действие над проектом с единым обработчиком ошибок и редиректами.
     *
     * @param projectId идентификатор проекта
     * @param actionLabel название действия для лога
     * @param redirectAttributes атрибуты flash-сообщений
     * @param successRedirect URL при успешном выполнении
     * @param failureRedirect URL при ошибке
     * @param action выполняемое действие
     * @return URL перенаправления после выполнения
     */
    private String executeProjectAction(UUID projectId,
                                        String actionLabel,
                                        RedirectAttributes redirectAttributes,
                                        String successRedirect,
                                        String failureRedirect,
                                        Runnable action) {
        try {
            action.run();
            return successRedirect;
        } catch (Exception e) {
            log.warn("Ошибка {} проекта {}: {}", actionLabel, projectId, e.getMessage());
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    webErrorMessageService.resolve(e, "Не удалось выполнить действие для проекта. Попробуйте еще раз.")
            );
            return failureRedirect;
        }
    }

    /**
     * Формирует redirect-URL на указанную вкладку проекта.
     *
     * @param projectId идентификатор проекта
     * @param tab имя вкладки
     * @return URL перенаправления
     */
    private String redirectToTab(UUID projectId, String tab) {
        return "redirect:/projects/" + projectId + "?tab=" + tab;
    }

    /**
     * Строит модель канбан-доски из списка задач.
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
     * @param tasksByStatus задачи, сгруппированные по статусу
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
