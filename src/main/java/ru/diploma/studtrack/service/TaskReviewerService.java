package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskReviewerRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskReviewerService {

    private final TaskReviewerRepository taskReviewerRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final ProjectService projectService;

    public List<TaskReviewer> getReviewersByTask(UUID taskId) {
        return taskReviewerRepository.findByTaskId(taskId);
    }

    public List<TaskReviewer> getPendingReviewsForCurrentUser() {
        UUID currentUserId = userService.getCurrentUserId();
        return taskReviewerRepository.findByUserId(currentUserId).stream()
                .filter(tr -> tr.getStatus() == TaskReviewer.ReviewStatus.PENDING)
                .toList();
    }

    @Transactional
    public TaskReviewer addReviewer(UUID taskId, UUID reviewerId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        if (taskReviewerRepository.existsByTaskIdAndUserId(taskId, reviewerId)) {
            throw new AlreadyExistsException("Ревьюер уже назначен на эту задачу");
        }

        User reviewer = userService.findById(reviewerId);

        TaskReviewer taskReviewer = TaskReviewer.builder()
                .task(task)
                .user(reviewer)
                .status(TaskReviewer.ReviewStatus.PENDING)
                .build();

        return taskReviewerRepository.save(taskReviewer);
    }

    @Transactional
    public void removeReviewer(UUID taskId, UUID reviewerId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        taskReviewerRepository.deleteByTaskIdAndUserId(taskId, reviewerId);
    }

    @Transactional
    public TaskReviewer submitReview(UUID taskId, UUID reviewerId, TaskReviewer.ReviewStatus status, String comment) {
        TaskReviewer taskReviewer = taskReviewerRepository.findByTaskIdAndUserId(taskId, reviewerId)
                .orElseThrow(() -> new NotFoundException("Назначение ревьюера не найдено"));

        UUID currentUserId = userService.getCurrentUserId();
        if (!taskReviewer.getUser().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Вы можете отправлять ревью только от своего имени");
        }

        taskReviewer.setStatus(status);
        taskReviewer.setComment(comment);

        return taskReviewerRepository.save(taskReviewer);
    }

    public boolean isApprovedByMajority(UUID taskId) {
        List<TaskReviewer> reviewers = taskReviewerRepository.findByTaskId(taskId);

        if (reviewers.isEmpty()) {
            return true; // Нет ревьюеров — считаем, что одобрено
        }

        long approvedCount = reviewers.stream()
                .filter(tr -> tr.getStatus() == TaskReviewer.ReviewStatus.APPROVED)
                .count();

        // Строго больше половины
        return approvedCount * 2 > reviewers.size();
    }
}