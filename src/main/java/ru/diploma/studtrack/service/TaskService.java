package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskRepository;
import ru.diploma.studtrack.repository.TaskReviewerRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskReviewerRepository taskReviewerRepository;
    private final ProjectService projectService;
    private final UserService userService;

    public List<Task> getTasksByProject(UUID projectId) {
        projectService.checkMembership(projectId);
        return taskRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public List<Task> getMyTasks(UUID projectId) {
        projectService.checkMembership(projectId);
        UUID currentUserId = userService.getCurrentUserId();
        return taskRepository.findByProjectIdAndAssigneeId(projectId, currentUserId);
    }

    public Task findById(UUID id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Задача", id));
    }

    @Transactional
    public Task create(UUID projectId, String title, String description, Task.Priority priority, boolean reviewRequired, UUID assigneeId) {
        projectService.checkMembership(projectId);
        Project project = projectService.findById(projectId);

        Task.TaskBuilder builder = Task.builder()
                .project(project)
                .title(title)
                .description(description)
                .status(Task.TaskStatus.BACKLOG)
                .priority(priority)
                .reviewRequired(reviewRequired);

        if (assigneeId != null) {
            User assignee = userService.findById(assigneeId);
            builder.assignee(assignee);
        }

        return taskRepository.save(builder.build());
    }

    @Transactional
    public Task update(UUID id, String title, String description, Task.Priority priority, UUID assigneeId) {
        Task task = findById(id);
        projectService.checkMembership(task.getProject().getId());

        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);

        if (assigneeId != null) {
            User assignee = userService.findById(assigneeId);
            task.setAssignee(assignee);
        } else {
            task.setAssignee(null);
        }

        return taskRepository.save(task);
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

        if (task.isReviewRequired() && newStatus == Task.TaskStatus.REVIEW) {
            validateReadyForReview(task);
        }

        if (newStatus == Task.TaskStatus.DONE) {
            validateCanComplete(task);
        }

        task.setStatus(newStatus);
        return taskRepository.save(task);
    }

    private void validateReadyForReview(Task task) {
        if (task.getAssignee() == null) {
            throw new InvalidStateException(
                    "Невозможно отправить задачу на проверку",
                    "исполнитель не назначен",
                    "назначьте исполнителя"
            );
        }
    }

    private void validateCanComplete(Task task) {
        UUID currentUserId = userService.getCurrentUserId();
        Project project = task.getProject();

        if (!project.getOwner().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Завершить задачу может только владелец проекта");
        }

        if (task.isReviewRequired()) {
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

    private boolean isApprovedByMajority(UUID taskId) {
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