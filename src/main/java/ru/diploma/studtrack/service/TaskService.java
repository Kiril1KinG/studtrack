package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskAssigneeRepository;
import ru.diploma.studtrack.repository.TaskRepository;
import ru.diploma.studtrack.repository.TaskReviewerRepository;
import ru.diploma.studtrack.repository.TaskReviewRoundRepository;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {
    public record ReviewStats(long approved, long rejected, long pending) {}

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskReviewerRepository taskReviewerRepository;
    private final TaskReviewRoundRepository taskReviewRoundRepository;
    private final ProjectService projectService;
    private final UserService userService;
    private final TaskHistoryService taskHistoryService;

    public List<Task> getTasksByProject(UUID projectId) {
        projectService.checkMembership(projectId);
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<Task> getMyTasks(UUID projectId) {
        projectService.checkMembership(projectId);
        UUID currentUserId = userService.getCurrentUserId();
        Map<UUID, Task> merged = new LinkedHashMap<>();
        taskAssigneeRepository.findByUserId(currentUserId).stream()
                .map(ta -> ta.getTask())
                .filter(t -> t.getProject().getId().equals(projectId))
                .forEach(t -> merged.put(t.getId(), t));
        return List.copyOf(merged.values());
    }

    public List<Task> getAssignedToMe() {
        UUID currentUserId = userService.getCurrentUserId();
        Map<UUID, Task> merged = new LinkedHashMap<>();
        taskAssigneeRepository.findByUserId(currentUserId).stream()
                .map(ta -> ta.getTask())
                .forEach(t -> merged.put(t.getId(), t));
        return List.copyOf(merged.values());
    }

    public Map<UUID, String> getReviewStateByTaskId(List<Task> tasks) {
        Map<UUID, String> reviewStateByTaskId = new HashMap<>();
        for (Task task : tasks) {
            String state = "NONE";
            if (task.isReviewRequired() && task.getStatus() == Task.TaskStatus.REVIEW) {
                if (canMoveToDoneByReview(task.getId())) {
                    state = "READY";
                } else if (hasOpenReviewRound(task.getId())) {
                    state = "LOCKED";
                }
            }
            reviewStateByTaskId.put(task.getId(), state);
        }
        return reviewStateByTaskId;
    }

    public Map<UUID, ReviewStats> getReviewStatsByTaskId(List<Task> tasks) {
        Map<UUID, ReviewStats> reviewStatsByTaskId = new HashMap<>();
        for (Task task : tasks) {
            List<TaskReviewer> reviewers = taskReviewerRepository.findByTaskId(task.getId());
            long approved = reviewers.stream().filter(r -> r.getStatus() == TaskReviewer.ReviewStatus.APPROVED).count();
            long rejected = reviewers.stream().filter(r -> r.getStatus() == TaskReviewer.ReviewStatus.REJECTED).count();
            long pending = reviewers.stream().filter(r -> r.getStatus() == TaskReviewer.ReviewStatus.PENDING).count();
            reviewStatsByTaskId.put(task.getId(), new ReviewStats(approved, rejected, pending));
        }
        return reviewStatsByTaskId;
    }

    public List<TaskReviewer> getPendingReviewsForMe() {
        UUID currentUserId = userService.getCurrentUserId();
        return taskReviewerRepository.findByUserIdAndStatus(currentUserId, TaskReviewer.ReviewStatus.PENDING)
                .stream()
                .filter(tr -> tr.getTask().isReviewRequired())
                .toList();
    }

    public Task findById(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Задача", id));
    }

    @Transactional
    public Task create(UUID projectId, String title, String description, Task.Priority priority, boolean reviewRequired, UUID assigneeId, LocalDate deadline) {
        projectService.checkMembership(projectId);
        Project project = projectService.findById(projectId);

        Task.TaskBuilder builder = Task.builder()
                .project(project)
                .title(title)
                .description(description)
                .status(Task.TaskStatus.BACKLOG)
                .priority(priority)
                .reviewRequired(reviewRequired)
                .deadline(deadline);

        Task task = taskRepository.save(builder.build());
        if (assigneeId != null) {
            addAssigneeLink(task, assigneeId);
        }
        return task;
    }

    @Transactional
    public Task update(UUID id, String title, String description, Task.Priority priority, UUID assigneeId, boolean reviewRequired, LocalDate deadline) {
        Task task = findById(id);
        projectService.checkMembership(task.getProject().getId());
        User actor = userService.getCurrentUser();
        boolean canManageReviewFlag = projectService.isOwner(task.getProject().getId(), actor.getId()) || isAssignee(task, actor.getId());
        boolean reviewWasRequired = task.isReviewRequired();
        String oldTitle = task.getTitle();
        String oldDescription = task.getDescription();
        Task.Priority oldPriority = task.getPriority();
        boolean oldReviewRequired = task.isReviewRequired();
        LocalDate oldDeadline = task.getDeadline();

        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        if (reviewWasRequired != reviewRequired && !canManageReviewFlag) {
            throw new AccessDeniedException("Изменять флаг \"Требуется ревью\" может только владелец или исполнитель задачи");
        }
        task.setReviewRequired(reviewRequired);
        task.setDeadline(deadline);

        if (reviewWasRequired && !reviewRequired && task.getStatus() == Task.TaskStatus.REVIEW) {
            task.setStatus(Task.TaskStatus.IN_PROGRESS);
        }
        if (reviewWasRequired && !reviewRequired) {
            closeOpenReviewRounds(task, actor);
            removeAllReviewers(task.getId());
        }

        if (assigneeId == null && reviewRequired && task.getStatus() == Task.TaskStatus.REVIEW && !hasAnyAssignee(task)) {
            throw new InvalidStateException(
                    "Нельзя снять исполнителя",
                    "задача находится на ревью",
                    "назначьте исполнителя перед продолжением ревью"
            );
        }

        if (assigneeId != null) {
            if (taskReviewerRepository.existsByTaskIdAndUserId(id, assigneeId)) {
                throw new InvalidStateException(
                        "Нельзя назначить исполнителя",
                        "пользователь уже является ревьюером этой задачи",
                        "снимите пользователя с ревьюеров или выберите другого исполнителя"
                );
            }
            addAssigneeLink(task, assigneeId);
        }

        Task saved = taskRepository.save(task);
        taskHistoryService.recordFieldChange(saved, actor, "title", oldTitle, title);
        taskHistoryService.recordFieldChange(saved, actor, "description", oldDescription, description);
        taskHistoryService.recordFieldChange(saved, actor, "priority", oldPriority, priority);
        taskHistoryService.recordFieldChange(saved, actor, "reviewRequired", oldReviewRequired, reviewRequired);
        taskHistoryService.recordFieldChange(saved, actor, "deadline", oldDeadline, deadline);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        Task task = findById(id);
        projectService.checkMembership(task.getProject().getId());
        taskRepository.delete(task);
    }

    @Transactional
    public Task changeStatus(UUID id, Task.TaskStatus newStatus) {
        Task task = findById(id);
        projectService.checkMembership(task.getProject().getId());
        Task.TaskStatus oldStatus = task.getStatus();

        User currentUser = userService.getCurrentUser();

        if (task.isReviewRequired() && hasOpenReviewRound(task.getId()) && newStatus != Task.TaskStatus.REVIEW) {
            boolean canMoveToDone = newStatus == Task.TaskStatus.DONE && canMoveToDoneByReview(task.getId());
            if (!canMoveToDone) {
                throw new InvalidStateException(
                        "Нельзя изменить статус задачи",
                        "идёт активный раунд ревью",
                        "дождитесь одобрения большинства ревьюеров или отключите ревью"
                );
            }
        }

        if (newStatus == Task.TaskStatus.IN_PROGRESS && !hasAnyAssignee(task)) {
            ensureUserIsNotReviewer(task.getId(), currentUser.getId());
            addAssigneeLink(task, currentUser.getId());
        }

        if (newStatus == Task.TaskStatus.REVIEW) {
            if (!task.isReviewRequired()) {
                task.setReviewRequired(true);
            }
            if (!hasAnyAssignee(task)) {
                ensureUserIsNotReviewer(task.getId(), currentUser.getId());
                addAssigneeLink(task, currentUser.getId());
            }
            validateReadyForReview(task);
            autoCreateFirstReviewRound(task, currentUser);
        }

        if (newStatus == Task.TaskStatus.DONE) {
            validateCanComplete(task);
            closeOpenReviewRoundsAsCompleted(task.getId());
        }

        if (oldStatus == Task.TaskStatus.DONE && newStatus != Task.TaskStatus.DONE) {
            resetReviewersToPending(task.getId());
        }

        task.setStatus(newStatus);
        Task saved = taskRepository.save(task);
        taskHistoryService.recordEvent(saved, currentUser, TaskHistory.EventType.TASK_STATUS_CHANGED, Map.of(
                "oldStatus", oldStatus.name(),
                "newStatus", newStatus.name()
        ));
        return saved;
    }

    private void autoCreateFirstReviewRound(Task task, User initiator) {
        Integer maxRound = taskReviewRoundRepository.findMaxRoundNumberByTaskId(task.getId());
        if (maxRound == null) {
            TaskReviewRound round = TaskReviewRound.builder()
                    .task(task)
                    .roundNumber(1)
                    .initiator(initiator)
                    .summaryComment("Раунд ревью #1")
                    .status(TaskReviewRound.RoundStatus.OPEN)
                    .build();
            taskReviewRoundRepository.save(round);
        }
    }

    private void validateReadyForReview(Task task) {
        if (!hasAnyAssignee(task)) {
            throw new InvalidStateException(
                    "Невозможно отправить задачу на проверку",
                    "исполнитель не назначен",
                    "назначьте исполнителя"
            );
        }
    }

    private void validateCanComplete(Task task) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!isAssignee(task, currentUserId)) {
            throw new AccessDeniedException("Завершить задачу может только её исполнитель");
        }

        if (task.isReviewRequired() && hasActiveReviewers(task.getId())) {
            if (task.getStatus() != Task.TaskStatus.REVIEW) {
                throw new InvalidStateException(
                        "Невозможно завершить задачу",
                        "задача требует проверки, но не находится в статусе REVIEW",
                        "сначала отправьте задачу на проверку"
                );
            }

            if (!isApprovedByMajority(task.getId())) {
                throw new InvalidStateException(
                        "Невозможно завершить задачу",
                        "недостаточно одобрений от ревьюеров",
                        "дождитесь одобрения более половины ревьюеров"
                );
            }
        }
    }

    private boolean hasActiveReviewers(UUID taskId) {
        return !taskReviewerRepository.findByTaskId(taskId).isEmpty();
    }

    private void closeOpenReviewRounds(Task task, User actor) {
        UUID taskId = task.getId();
        List<TaskReviewRound> rounds = taskReviewRoundRepository.findByTaskIdOrderByRoundNumberDesc(taskId);
        List<TaskReviewRound> canceledRounds = rounds.stream()
                .filter(round -> round.getStatus() == TaskReviewRound.RoundStatus.OPEN)
                .toList();
        canceledRounds.forEach(round -> round.setStatus(TaskReviewRound.RoundStatus.CANCELED));
        taskReviewRoundRepository.saveAll(rounds);
        canceledRounds.forEach(round -> taskHistoryService.recordEvent(task, actor,
                TaskHistory.EventType.REVIEW_ROUND_CANCELED,
                Map.of("roundNumber", round.getRoundNumber())));
    }

    private void closeOpenReviewRoundsAsCompleted(UUID taskId) {
        List<TaskReviewRound> rounds = taskReviewRoundRepository.findByTaskIdOrderByRoundNumberDesc(taskId);
        rounds.stream()
                .filter(round -> round.getStatus() == TaskReviewRound.RoundStatus.OPEN)
                .forEach(round -> round.setStatus(TaskReviewRound.RoundStatus.COMPLETED));
        taskReviewRoundRepository.saveAll(rounds);
    }

    private void removeAllReviewers(UUID taskId) {
        taskReviewerRepository.deleteByTaskId(taskId);
    }

    private void resetReviewersToPending(UUID taskId) {
        List<TaskReviewer> reviewers = taskReviewerRepository.findByTaskId(taskId);
        reviewers.forEach(reviewer -> reviewer.setStatus(TaskReviewer.ReviewStatus.PENDING));
        taskReviewerRepository.saveAll(reviewers);
    }

    private boolean hasOpenReviewRound(UUID taskId) {
        return taskReviewRoundRepository.findByTaskIdOrderByRoundNumberDesc(taskId).stream()
                .anyMatch(round -> round.getStatus() == TaskReviewRound.RoundStatus.OPEN);
    }

    private boolean canMoveToDoneByReview(UUID taskId) {
        return hasActiveReviewers(taskId) && isApprovedByMajority(taskId);
    }

    private void addAssigneeLink(Task task, UUID userId) {
        User user = userService.findById(userId);
        if (!taskAssigneeRepository.existsByTaskIdAndUserId(task.getId(), userId)) {
            taskAssigneeRepository.save(TaskAssignee.builder()
                    .task(task)
                    .user(user)
                    .build());
        }
    }

    private boolean hasAnyAssignee(Task task) {
        return taskAssigneeRepository.findByTaskId(task.getId()).stream().findAny().isPresent();
    }

    private boolean isAssignee(Task task, UUID userId) {
        return taskAssigneeRepository.existsByTaskIdAndUserId(task.getId(), userId);
    }

    private void ensureUserIsNotReviewer(UUID taskId, UUID userId) {
        if (taskReviewerRepository.existsByTaskIdAndUserId(taskId, userId)) {
            throw new InvalidStateException(
                    "Нельзя назначить исполнителя",
                    "пользователь уже является ревьюером этой задачи",
                    "снимите пользователя с ревьюеров или выберите другого исполнителя"
            );
        }
    }

    private boolean isApprovedByMajority(UUID taskId) {
        List<TaskReviewer> reviewers = taskReviewerRepository.findByTaskId(taskId);
        if (reviewers.isEmpty()) {
            return false;
        }
        long approvedCount = reviewers.stream()
                .filter(tr -> tr.getStatus() == TaskReviewer.ReviewStatus.APPROVED)
                .count();
        return approvedCount * 2 > reviewers.size();
    }
}