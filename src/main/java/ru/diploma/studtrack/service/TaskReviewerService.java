package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskReviewerRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * Управляет ревьюерами задачи и обработкой результатов ревью.
 */
public class TaskReviewerService {

    /**
     * Репозиторий связей «задача-ревьюер».
     */
    private final TaskReviewerRepository taskReviewerRepository;
    /**
     * Сервис чтения и изменения задач.
     */
    private final TaskService taskService;
    /**
     * Сервис операций с исполнителями задачи.
     */
    private final TaskAssigneeService taskAssigneeService;
    /**
     * Сервис работы с пользователями.
     */
    private final UserService userService;
    /**
     * Сервис проверки прав доступа в проекте.
     */
    private final ProjectService projectService;
    /**
     * Сервис отправки уведомлений.
     */
    private final NotificationService notificationService;
    /**
     * Сервис фиксации истории задачи.
     */
    private final TaskHistoryService taskHistoryService;

    /**
     * Возвращает ревьюеров задачи.
     *
     * @param taskId идентификатор задачи
     * @return список ревьюеров
     */
    public List<TaskReviewer> getReviewersByTask(UUID taskId) {
        return taskReviewerRepository.findByTaskId(taskId);
    }

    /**
     * Возвращает ожидающие ревью текущего пользователя.
     *
     * @return список ожидающих ревью
     */
    public List<TaskReviewer> getPendingReviewsForCurrentUser() {
        UUID currentUserId = userService.getCurrentUserId();
        return taskReviewerRepository.findByUserId(currentUserId).stream()
                .filter(tr -> tr.getStatus() == TaskReviewer.ReviewStatus.PENDING)
                .filter(tr -> tr.getTask().isReviewRequired())
                .toList();
    }

    /**
     * Возвращает ожидающие ревью текущего пользователя по конкретной задаче.
     *
     * @param taskId идентификатор задачи
     * @return список ожидающих ревью по задаче
     */
    public List<TaskReviewer> getPendingReviewsForCurrentUserByTask(UUID taskId) {
        UUID currentUserId = userService.getCurrentUserId();
        return taskReviewerRepository.findByTaskId(taskId).stream()
                .filter(tr -> tr.getUser().getId().equals(currentUserId))
                .filter(tr -> tr.getStatus() == TaskReviewer.ReviewStatus.PENDING)
                .filter(tr -> tr.getTask().isReviewRequired())
                .toList();
    }

    @Transactional
    /**
     * Добавляет ревьюера к задаче с проверкой ролей и ограничений.
     *
     * @param taskId идентификатор задачи
     * @param reviewerId идентификатор пользователя-ревьюера
     * @return созданное назначение ревьюера
     */
    public TaskReviewer addReviewer(UUID taskId, UUID reviewerId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        User actor = userService.getCurrentUser();
        UUID currentUserId = actor.getId();
        boolean isOwner = projectService.isOwner(task.getProject().getId(), currentUserId);
        boolean isAssignee = taskAssigneeService.isAssignee(taskId, currentUserId);
        boolean selfAssign = reviewerId.equals(currentUserId);
        if (!isOwner && !isAssignee && !selfAssign) {
            throw new AccessDeniedException("Добавить ревьюера может владелец или исполнитель задачи");
        }
        if (!task.isReviewRequired()) {
            throw new AccessDeniedException("Нельзя добавить ревьюера: у задачи отключено ревью");
        }

        if (taskAssigneeService.isAssignee(taskId, reviewerId)) {
            throw new AccessDeniedException("Исполнитель задачи не может быть её ревьюером");
        }

        if (taskReviewerRepository.existsByTaskIdAndUserId(taskId, reviewerId)) {
            throw new AlreadyExistsException("Ревьюер уже назначен на эту задачу");
        }

        User reviewer = userService.findById(reviewerId);

        TaskReviewer taskReviewer = TaskReviewer.builder()
                .task(task)
                .user(reviewer)
                .status(TaskReviewer.ReviewStatus.PENDING)
                .build();

        TaskReviewer saved = taskReviewerRepository.save(taskReviewer);
        notificationService.notifyReviewerAssigned(reviewer, actor, task);
        taskHistoryService.recordEvent(task, actor, TaskHistory.EventType.REVIEWER_ADDED, Map.of(
                "reviewerId", reviewer.getId(),
                "reviewerName", reviewer.getFullName()
        ));
        return saved;
    }

    @Transactional
    /**
     * Удаляет ревьюера из задачи.
     *
     * @param taskId идентификатор задачи
     * @param reviewerId идентификатор пользователя-ревьюера
     */
    public void removeReviewer(UUID taskId, UUID reviewerId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        User actor = userService.getCurrentUser();
        UUID currentUserId = actor.getId();
        boolean isOwner = projectService.isOwner(task.getProject().getId(), currentUserId);
        boolean isAssignee = taskAssigneeService.isAssignee(taskId, currentUserId);
        boolean selfRemove = currentUserId.equals(reviewerId);
        if (!isOwner && !isAssignee && !selfRemove) {
            throw new AccessDeniedException("Удалить ревьюера может владелец, исполнитель или сам ревьюер");
        }

        User reviewer = userService.findById(reviewerId);
        taskReviewerRepository.deleteByTaskIdAndUserId(taskId, reviewerId);
        taskHistoryService.recordEvent(task, actor, TaskHistory.EventType.REVIEWER_REMOVED, Map.of(
                "reviewerId", reviewer.getId(),
                "reviewerName", reviewer.getFullName()
        ));
    }

    @Transactional
    /**
     * Сохраняет результат ревью от назначенного ревьюера.
     *
     * @param taskId идентификатор задачи
     * @param reviewerId идентификатор ревьюера
     * @param status итоговый статус ревью
     * @param comment комментарий ревью
     * @return обновлённая запись ревьюера
     */
    public TaskReviewer submitReview(UUID taskId, UUID reviewerId, TaskReviewer.ReviewStatus status, String comment) {
        TaskReviewer taskReviewer = taskReviewerRepository.findByTaskIdAndUserId(taskId, reviewerId)
                .orElseThrow(() -> new NotFoundException("Назначение ревьюера не найдено"));
        if (!taskReviewer.getTask().isReviewRequired()) {
            throw new AccessDeniedException("Нельзя отправить ревью: у задачи отключено ревью");
        }

        UUID currentUserId = userService.getCurrentUserId();
        if (!taskReviewer.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Вы можете отправлять ревью только от своего имени");
        }
        User actor = userService.getCurrentUser();

        taskReviewer.setStatus(status);
        taskReviewer.setComment(comment);

        TaskReviewer saved = taskReviewerRepository.save(taskReviewer);
        taskHistoryService.recordEvent(taskReviewer.getTask(), actor, TaskHistory.EventType.REVIEW_SUBMITTED, Map.of(
                "reviewerId", actor.getId(),
                "reviewerName", actor.getFullName(),
                "status", status.name()
        ));
        return saved;
    }

    /**
     * Проверяет, одобрена ли задача большинством ревьюеров.
     *
     * @param taskId идентификатор задачи
     * @return true, если одобрений больше половины
     */
    public boolean isApprovedByMajority(UUID taskId) {
        List<TaskReviewer> reviewers = taskReviewerRepository.findByTaskId(taskId);

        if (reviewers.isEmpty()) {
            return true;
        }

        long approvedCount = reviewers.stream()
                .filter(tr -> tr.getStatus() == TaskReviewer.ReviewStatus.APPROVED)
                .count();

        return approvedCount * 2 > reviewers.size();
    }
}