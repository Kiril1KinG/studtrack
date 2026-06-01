package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskAssigneeRepository;
import ru.diploma.studtrack.repository.TaskRepository;
import ru.diploma.studtrack.repository.TaskReviewerRepository;
import ru.diploma.studtrack.repository.TaskReviewRoundRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskAssigneeRepository taskAssigneeRepository;
    @Mock private TaskReviewerRepository taskReviewerRepository;
    @Mock private TaskReviewRoundRepository taskReviewRoundRepository;
    @Mock private ProjectService projectService;
    @Mock private UserService userService;
    @Mock private TaskHistoryService taskHistoryService;

    private TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(
                taskRepository,
                taskAssigneeRepository,
                taskReviewerRepository,
                taskReviewRoundRepository,
                projectService,
                userService,
                taskHistoryService
        );
    }

    @Test
    void createShouldSaveBacklogTaskAndAssigneeLink() {
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Task savedTask = Task.builder().id(UUID.randomUUID()).project(project).status(Task.TaskStatus.BACKLOG).build();
        when(projectService.findById(projectId)).thenReturn(project);
        when(taskRepository.save(any(Task.class))).thenReturn(savedTask);
        when(userService.findById(assigneeId)).thenReturn(User.builder().id(assigneeId).build());
        when(taskAssigneeRepository.existsByTaskIdAndUserId(savedTask.getId(), assigneeId)).thenReturn(false);

        Task created = service.create(projectId, "t", "d", Task.Priority.MEDIUM, false, assigneeId, LocalDate.now());

        assertEquals(Task.TaskStatus.BACKLOG, created.getStatus());
        verify(taskAssigneeRepository).save(any(TaskAssignee.class));
    }

    @Test
    void getMyTasksShouldReturnUniqueAssignedInProject() {
        UUID projectId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Task inProject = Task.builder().id(UUID.randomUUID()).project(Project.builder().id(projectId).build()).build();
        Task otherProject = Task.builder().id(UUID.randomUUID()).project(Project.builder().id(UUID.randomUUID()).build()).build();
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(taskAssigneeRepository.findByUserId(currentUserId)).thenReturn(List.of(
                TaskAssignee.builder().task(inProject).build(),
                TaskAssignee.builder().task(inProject).build(),
                TaskAssignee.builder().task(otherProject).build()
        ));

        List<Task> result = service.getMyTasks(projectId);
        assertEquals(List.of(inProject), result);
    }

    @Test
    void getAssignedToMeShouldReturnUniqueTasks() {
        UUID currentUserId = UUID.randomUUID();
        Task first = Task.builder().id(UUID.randomUUID()).project(Project.builder().id(UUID.randomUUID()).build()).build();
        Task second = Task.builder().id(UUID.randomUUID()).project(Project.builder().id(UUID.randomUUID()).build()).build();
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(taskAssigneeRepository.findByUserId(currentUserId)).thenReturn(List.of(
                TaskAssignee.builder().task(first).build(),
                TaskAssignee.builder().task(first).build(),
                TaskAssignee.builder().task(second).build()
        ));

        List<Task> result = service.getAssignedToMe();
        assertEquals(List.of(first, second), result);
    }

    @Test
    void getPendingReviewsForMeShouldFilterOutNonReviewTasks() {
        UUID currentUserId = UUID.randomUUID();
        Task reviewTask = Task.builder().reviewRequired(true).build();
        Task noReviewTask = Task.builder().reviewRequired(false).build();
        TaskReviewer pendingReview = TaskReviewer.builder().task(reviewTask).status(TaskReviewer.ReviewStatus.PENDING).build();
        TaskReviewer pendingNoReview = TaskReviewer.builder().task(noReviewTask).status(TaskReviewer.ReviewStatus.PENDING).build();
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(taskReviewerRepository.findByUserIdAndStatus(currentUserId, TaskReviewer.ReviewStatus.PENDING))
                .thenReturn(List.of(pendingReview, pendingNoReview));

        List<TaskReviewer> result = service.getPendingReviewsForMe();
        assertEquals(List.of(pendingReview), result);
    }

    @Test
    void getReviewStatsByTaskIdShouldAggregateStatuses() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        when(taskReviewerRepository.findByTaskId(task.getId())).thenReturn(List.of(
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build(),
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.REJECTED).build(),
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.PENDING).build(),
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build()
        ));

        var map = service.getReviewStatsByTaskId(List.of(task));
        TaskService.ReviewStats stats = map.get(task.getId());
        assertEquals(2, stats.approved());
        assertEquals(1, stats.rejected());
        assertEquals(1, stats.pending());
    }

    @Test
    void deleteShouldRemoveTaskAfterAccessCheck() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));

        service.delete(taskId);
        verify(taskRepository).delete(task);
    }

    @Test
    void updateShouldRejectReviewFlagChangeForUnauthorizedUser() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder()
                .id(taskId)
                .project(project)
                .title("old")
                .description("old")
                .priority(Task.Priority.LOW)
                .reviewRequired(false)
                .status(Task.TaskStatus.BACKLOG)
                .build();
        User actor = User.builder().id(actorId).build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(actor);
        when(projectService.isOwner(project.getId(), actorId)).thenReturn(false);
        when(taskAssigneeRepository.existsByTaskIdAndUserId(taskId, actorId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.update(
                taskId, "n", "n", Task.Priority.HIGH, null, true, LocalDate.now()
        ));
    }

    @Test
    void updateShouldCancelOpenRoundsAndRemoveReviewersWhenReviewDisabled() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder()
                .id(taskId)
                .project(project)
                .title("old")
                .description("old")
                .priority(Task.Priority.LOW)
                .reviewRequired(true)
                .status(Task.TaskStatus.REVIEW)
                .build();
        User actor = User.builder().id(actorId).build();
        TaskReviewRound openRound = TaskReviewRound.builder().status(TaskReviewRound.RoundStatus.OPEN).roundNumber(1).build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(actor);
        when(projectService.isOwner(project.getId(), actorId)).thenReturn(true);
        when(taskReviewRoundRepository.findByTaskIdOrderByRoundNumberDesc(taskId)).thenReturn(List.of(openRound));
        when(taskRepository.save(task)).thenReturn(task);

        Task updated = service.update(taskId, "n", "n", Task.Priority.HIGH, null, false, LocalDate.now());
        assertEquals(Task.TaskStatus.IN_PROGRESS, updated.getStatus());
        verify(taskReviewerRepository).deleteByTaskId(taskId);
        verify(taskReviewRoundRepository).saveAll(List.of(openRound));
    }

    @Test
    void changeStatusToReviewShouldAutoAssignAndCreateRound() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .project(Project.builder().id(UUID.randomUUID()).build())
                .status(Task.TaskStatus.BACKLOG)
                .reviewRequired(false)
                .build();
        User actor = User.builder().id(userId).build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(actor);
        when(taskReviewerRepository.existsByTaskIdAndUserId(taskId, userId)).thenReturn(false);
        when(userService.findById(userId)).thenReturn(actor);
        when(taskAssigneeRepository.findByTaskId(taskId)).thenReturn(List.of(), List.of(TaskAssignee.builder().task(task).user(actor).build()));
        when(taskAssigneeRepository.existsByTaskIdAndUserId(taskId, userId)).thenReturn(false);
        when(taskReviewRoundRepository.findMaxRoundNumberByTaskId(taskId)).thenReturn(null);
        when(taskRepository.save(task)).thenReturn(task);

        Task updated = service.changeStatus(taskId, Task.TaskStatus.REVIEW);
        assertEquals(Task.TaskStatus.REVIEW, updated.getStatus());
        verify(taskReviewRoundRepository).save(any(TaskReviewRound.class));
    }

    @Test
    void changeStatusToDoneShouldRequireAssignee() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .project(Project.builder().id(UUID.randomUUID()).build())
                .status(Task.TaskStatus.IN_PROGRESS)
                .reviewRequired(false)
                .build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(User.builder().id(actorId).build());
        when(userService.getCurrentUserId()).thenReturn(actorId);
        when(taskAssigneeRepository.existsByTaskIdAndUserId(taskId, actorId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.changeStatus(taskId, Task.TaskStatus.DONE));
    }

    @Test
    void changeStatusShouldRejectWhenOpenRoundBlocksTransition() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .project(Project.builder().id(UUID.randomUUID()).build())
                .status(Task.TaskStatus.REVIEW)
                .reviewRequired(true)
                .build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(User.builder().id(actorId).build());
        when(taskReviewRoundRepository.findByTaskIdOrderByRoundNumberDesc(taskId)).thenReturn(List.of(
                TaskReviewRound.builder().status(TaskReviewRound.RoundStatus.OPEN).build()
        ));

        assertThrows(InvalidStateException.class, () -> service.changeStatus(taskId, Task.TaskStatus.IN_PROGRESS));
    }

    @Test
    void changeStatusToDoneShouldCloseOpenRoundsWhenReviewPassed() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .project(Project.builder().id(UUID.randomUUID()).build())
                .status(Task.TaskStatus.REVIEW)
                .reviewRequired(true)
                .build();
        TaskReviewRound openRound = TaskReviewRound.builder().status(TaskReviewRound.RoundStatus.OPEN).build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(User.builder().id(actorId).build());
        when(userService.getCurrentUserId()).thenReturn(actorId);
        when(taskAssigneeRepository.existsByTaskIdAndUserId(taskId, actorId)).thenReturn(true);
        when(taskReviewerRepository.findByTaskId(taskId)).thenReturn(List.of(
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build(),
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build(),
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.REJECTED).build()
        ));
        when(taskReviewRoundRepository.findByTaskIdOrderByRoundNumberDesc(taskId)).thenReturn(List.of(openRound), List.of(openRound));
        when(taskRepository.save(task)).thenReturn(task);

        Task updated = service.changeStatus(taskId, Task.TaskStatus.DONE);
        assertEquals(Task.TaskStatus.DONE, updated.getStatus());
        verify(taskReviewRoundRepository).saveAll(List.of(openRound));
    }

    @Test
    void changeStatusFromDoneShouldResetReviewers() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .project(Project.builder().id(UUID.randomUUID()).build())
                .status(Task.TaskStatus.DONE)
                .reviewRequired(false)
                .build();
        TaskReviewer reviewer = TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(User.builder().id(actorId).build());
        when(taskRepository.save(task)).thenReturn(task);
        when(taskReviewerRepository.findByTaskId(taskId)).thenReturn(List.of(reviewer));

        Task updated = service.changeStatus(taskId, Task.TaskStatus.IN_PROGRESS);
        assertEquals(Task.TaskStatus.IN_PROGRESS, updated.getStatus());
        assertEquals(TaskReviewer.ReviewStatus.PENDING, reviewer.getStatus());
        verify(taskReviewerRepository).saveAll(List.of(reviewer));
    }

    @Test
    void updateShouldRejectAssigneeRemovalWhenReviewTaskHasNoAssignee() {
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder()
                .id(taskId)
                .project(project)
                .title("old")
                .description("old")
                .priority(Task.Priority.LOW)
                .reviewRequired(true)
                .status(Task.TaskStatus.REVIEW)
                .build();
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(userService.getCurrentUser()).thenReturn(User.builder().id(actorId).build());
        when(projectService.isOwner(project.getId(), actorId)).thenReturn(true);
        when(taskAssigneeRepository.findByTaskId(taskId)).thenReturn(List.of());

        assertThrows(InvalidStateException.class, () ->
                service.update(taskId, "n", "n", Task.Priority.MEDIUM, null, true, null));
    }

    @Test
    void getReviewStateByTaskIdShouldReturnReadyLockedAndNone() {
        Task ready = Task.builder().id(UUID.randomUUID()).reviewRequired(true).status(Task.TaskStatus.REVIEW).build();
        Task locked = Task.builder().id(UUID.randomUUID()).reviewRequired(true).status(Task.TaskStatus.REVIEW).build();
        Task none = Task.builder().id(UUID.randomUUID()).reviewRequired(false).status(Task.TaskStatus.BACKLOG).build();

        when(taskReviewerRepository.findByTaskId(ready.getId())).thenReturn(List.of(
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build()
        ));
        when(taskReviewerRepository.findByTaskId(locked.getId())).thenReturn(List.of(
                TaskReviewer.builder().status(TaskReviewer.ReviewStatus.PENDING).build()
        ));
        when(taskReviewRoundRepository.findByTaskIdOrderByRoundNumberDesc(locked.getId())).thenReturn(List.of(
                TaskReviewRound.builder().status(TaskReviewRound.RoundStatus.OPEN).build()
        ));

        var state = service.getReviewStateByTaskId(List.of(ready, locked, none));
        assertEquals("READY", state.get(ready.getId()));
        assertEquals("LOCKED", state.get(locked.getId()));
        assertEquals("NONE", state.get(none.getId()));
    }
}

