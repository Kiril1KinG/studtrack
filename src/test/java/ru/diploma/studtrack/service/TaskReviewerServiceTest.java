package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskReviewerRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReviewerServiceTest {

    @Mock private TaskReviewerRepository repository;
    @Mock private TaskService taskService;
    @Mock private TaskAssigneeService taskAssigneeService;
    @Mock private UserService userService;
    @Mock private ProjectService projectService;
    @Mock private NotificationService notificationService;
    @Mock private TaskHistoryService taskHistoryService;

    private TaskReviewerService service;

    @BeforeEach
    void setUp() {
        service = new TaskReviewerService(
                repository,
                taskService,
                taskAssigneeService,
                userService,
                projectService,
                notificationService,
                taskHistoryService
        );
    }

    @Test
    void getPendingReviewsForCurrentUserShouldFilterByStatusAndReviewFlag() {
        UUID userId = UUID.randomUUID();
        Task reviewTask = Task.builder().reviewRequired(true).build();
        Task noReviewTask = Task.builder().reviewRequired(false).build();
        TaskReviewer pending = TaskReviewer.builder().task(reviewTask).status(TaskReviewer.ReviewStatus.PENDING).build();
        TaskReviewer approved = TaskReviewer.builder().task(reviewTask).status(TaskReviewer.ReviewStatus.APPROVED).build();
        TaskReviewer pendingNoReview = TaskReviewer.builder().task(noReviewTask).status(TaskReviewer.ReviewStatus.PENDING).build();
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(repository.findByUserId(userId)).thenReturn(List.of(pending, approved, pendingNoReview));

        List<TaskReviewer> result = service.getPendingReviewsForCurrentUser();
        assertEquals(List.of(pending), result);
    }

    @Test
    void addReviewerShouldRejectDuplicate() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder().id(taskId).project(project).reviewRequired(true).build();
        User actor = User.builder().id(actorId).build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(projectService.isOwner(project.getId(), actorId)).thenReturn(true);
        when(taskAssigneeService.isAssignee(taskId, actorId)).thenReturn(false);
        when(taskAssigneeService.isAssignee(taskId, reviewerId)).thenReturn(false);
        when(repository.existsByTaskIdAndUserId(taskId, reviewerId)).thenReturn(true);

        assertThrows(AlreadyExistsException.class, () -> service.addReviewer(taskId, reviewerId));
    }

    @Test
    void addReviewerShouldSaveAndNotify() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder().id(taskId).project(project).reviewRequired(true).build();
        User actor = User.builder().id(actorId).firstName("Actor").lastName("A").build();
        User reviewer = User.builder().id(reviewerId).firstName("Rev").lastName("R").build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(projectService.isOwner(project.getId(), actorId)).thenReturn(true);
        when(taskAssigneeService.isAssignee(taskId, actorId)).thenReturn(false);
        when(taskAssigneeService.isAssignee(taskId, reviewerId)).thenReturn(false);
        when(repository.existsByTaskIdAndUserId(taskId, reviewerId)).thenReturn(false);
        when(userService.findById(reviewerId)).thenReturn(reviewer);
        when(repository.save(any(TaskReviewer.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskReviewer saved = service.addReviewer(taskId, reviewerId);
        assertEquals(TaskReviewer.ReviewStatus.PENDING, saved.getStatus());
        verify(notificationService).notifyReviewerAssigned(reviewer, actor, task);
    }

    @Test
    void removeReviewerShouldAllowSelfRemoval() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder().id(taskId).project(project).build();
        User actor = User.builder().id(reviewerId).build();
        User reviewer = User.builder().id(reviewerId).build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(projectService.isOwner(project.getId(), reviewerId)).thenReturn(false);
        when(taskAssigneeService.isAssignee(taskId, reviewerId)).thenReturn(false);
        when(userService.findById(reviewerId)).thenReturn(reviewer);

        service.removeReviewer(taskId, reviewerId);
        verify(repository).deleteByTaskIdAndUserId(taskId, reviewerId);
    }

    @Test
    void submitReviewShouldRejectAnotherUser() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).reviewRequired(true).build();
        TaskReviewer assignment = TaskReviewer.builder()
                .task(task)
                .user(User.builder().id(reviewerId).build())
                .status(TaskReviewer.ReviewStatus.PENDING)
                .build();
        when(repository.findByTaskIdAndUserId(taskId, reviewerId)).thenReturn(Optional.of(assignment));
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class,
                () -> service.submitReview(taskId, reviewerId, TaskReviewer.ReviewStatus.APPROVED, "ok"));
    }

    @Test
    void isApprovedByMajorityShouldWork() {
        UUID taskId = UUID.randomUUID();
        when(repository.findByTaskId(taskId)).thenReturn(List.of(
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build(),
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build(),
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.REJECTED).build()
        ));
        assertEquals(true, service.isApprovedByMajority(taskId));
    }
}

