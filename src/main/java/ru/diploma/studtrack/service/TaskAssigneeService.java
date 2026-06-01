package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskAssigneeRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * Управляет исполнителями задач: назначением, снятием и проверками доступа.
 */
public class TaskAssigneeService {

    /**
     * Репозиторий связей «задача-исполнитель».
     */
    private final TaskAssigneeRepository taskAssigneeRepository;
    /**
     * Сервис чтения и изменения задач.
     */
    private final TaskService taskService;
    /**
     * Сервис работы с пользователями.
     */
    private final UserService userService;
    /**
     * Сервис проверки членства и ролей в проекте.
     */
    private final ProjectService projectService;
    /**
     * Сервис отправки пользовательских уведомлений.
     */
    private final NotificationService notificationService;
    /**
     * Сервис фиксации истории изменений задачи.
     */
    private final TaskHistoryService taskHistoryService;

    /**
     * Возвращает всех исполнителей задачи.
     *
     * @param taskId идентификатор задачи
     * @return список исполнителей
     */
    public List<TaskAssignee> getAssigneesByTask(UUID taskId) {
        return taskAssigneeRepository.findByTaskId(taskId);
    }

    /**
     * Проверяет, назначен ли пользователь исполнителем задачи.
     *
     * @param taskId идентификатор задачи
     * @param userId идентификатор пользователя
     * @return true, если пользователь назначен исполнителем
     */
    public boolean isAssignee(UUID taskId, UUID userId) {
        return taskAssigneeRepository.existsByTaskIdAndUserId(taskId, userId);
    }

    @Transactional
    /**
     * Назначает исполнителя на задачу с проверкой полномочий.
     *
     * @param taskId идентификатор задачи
     * @param assigneeId идентификатор назначаемого пользователя
     * @return созданная связь «задача-исполнитель»
     */
    public TaskAssignee addAssignee(UUID taskId, UUID assigneeId) {
        Task task = findTask(taskId);
        projectService.checkMembership(task.getProject().getId());

        User actor = userService.getCurrentUser();
        UUID currentUserId = actor.getId();
        boolean isOwner = projectService.isOwner(task.getProject().getId(), currentUserId);
        boolean currentIsAssignee = isAssignee(taskId, currentUserId);
        boolean selfAssign = currentUserId.equals(assigneeId);
        if (!isOwner && !currentIsAssignee && !selfAssign) {
            throw new AccessDeniedException("Добавить исполнителя может владелец или исполнитель задачи");
        }
        if (taskAssigneeRepository.existsByTaskIdAndUserId(taskId, assigneeId)) {
            throw new AlreadyExistsException("Исполнитель уже назначен на эту задачу");
        }
        if (task.getReviewers().stream().anyMatch(r -> r.getUser().getId().equals(assigneeId))) {
            throw new AccessDeniedException("Ревьюер не может быть исполнителем этой задачи");
        }

        User assignee = userService.findById(assigneeId);
        TaskAssignee taskAssignee = TaskAssignee.builder()
                .task(task)
                .user(assignee)
                .build();
        TaskAssignee saved = taskAssigneeRepository.save(taskAssignee);
        notificationService.notifyTaskAssigned(assignee, actor, task);
        taskHistoryService.recordEvent(task, actor, TaskHistory.EventType.ASSIGNEE_ADDED, Map.of(
                "assigneeId", assignee.getId(),
                "assigneeName", assignee.getFullName()
        ));
        return saved;
    }

    @Transactional
    /**
     * Снимает исполнителя с задачи с учётом ограничений статуса ревью.
     *
     * @param taskId идентификатор задачи
     * @param assigneeId идентификатор пользователя
     */
    public void removeAssignee(UUID taskId, UUID assigneeId) {
        Task task = findTask(taskId);
        projectService.checkMembership(task.getProject().getId());

        User actor = userService.getCurrentUser();
        UUID currentUserId = actor.getId();
        boolean isOwner = projectService.isOwner(task.getProject().getId(), currentUserId);
        boolean currentIsAssignee = isAssignee(taskId, currentUserId);
        boolean selfRemove = currentUserId.equals(assigneeId);
        if (!isOwner && !currentIsAssignee && !selfRemove) {
            throw new AccessDeniedException("Удалить исполнителя может владелец, исполнитель или сам исполнитель");
        }

        List<TaskAssignee> currentAssignees = taskAssigneeRepository.findByTaskId(taskId);
        java.util.Set<UUID> uniqueAssigneeIds = new java.util.HashSet<>();
        currentAssignees.forEach(a -> uniqueAssigneeIds.add(a.getUser().getId()));
        uniqueAssigneeIds.remove(assigneeId);
        long remainingCount = uniqueAssigneeIds.size();
        if (task.isReviewRequired() && task.getStatus() == Task.TaskStatus.REVIEW && remainingCount <= 0) {
            throw new AccessDeniedException("Нельзя снять последнего исполнителя, пока задача находится на ревью");
        }

        User assignee = userService.findById(assigneeId);
        taskAssigneeRepository.deleteByTaskIdAndUserId(taskId, assigneeId);
        taskHistoryService.recordEvent(task, actor, TaskHistory.EventType.ASSIGNEE_REMOVED, Map.of(
                "assigneeId", assignee.getId(),
                "assigneeName", assignee.getFullName()
        ));
    }

    /**
     * Возвращает задачу по идентификатору.
     *
     * @param taskId идентификатор задачи
     * @return найденная задача
     */
    private Task findTask(UUID taskId) {
        return taskService.findById(taskId);
    }
}
