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
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskReviewRoundRepository;
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
class TaskReviewRoundServiceTest {

    @Mock private TaskReviewRoundRepository roundRepository;
    @Mock private TaskReviewerRepository reviewerRepository;
    @Mock private TaskService taskService;
    @Mock private TaskAssigneeService taskAssigneeService;
    @Mock private UserService userService;
    @Mock private ProjectService projectService;
    @Mock private NotificationService notificationService;
    @Mock private TaskHistoryService taskHistoryService;

    private TaskReviewRoundService service;

    @BeforeEach
    void setUp() {
        service = new TaskReviewRoundService(
                roundRepository,
                reviewerRepository,
                taskService,
                taskAssigneeService,
                userService,
                projectService,
                notificationService,
                taskHistoryService
        );
    }

    @Test
    void canStartNewRoundShouldBeTrueWhenNoRounds() {
        UUID taskId = UUID.randomUUID();
        when(roundRepository.findByTaskIdOrderByRoundNumberDesc(taskId)).thenReturn(List.of());
        assertEquals(true, service.canStartNewRound(taskId));
    }

    @Test
    void createNewRoundShouldPersistAndRecordHistory() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).reviewRequired(true).project(Project.builder().id(projectId).build()).build();
        User actor = User.builder().id(reviewerId).build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUserId()).thenReturn(reviewerId);
        when(reviewerRepository.existsByTaskIdAndUserId(taskId, reviewerId)).thenReturn(true);
        when(roundRepository.findByTaskIdOrderByRoundNumberDesc(taskId)).thenReturn(List.of());
        when(userService.getCurrentUser()).thenReturn(actor);
        when(roundRepository.findMaxRoundNumberByTaskId(taskId)).thenReturn(null);
        when(roundRepository.save(any(TaskReviewRound.class))).thenAnswer(inv -> {
            TaskReviewRound value = inv.getArgument(0);
            value.setId(UUID.randomUUID());
            return value;
        });

        TaskReviewRound round = service.createNewRound(taskId, "sum");

        assertEquals(TaskReviewRound.RoundStatus.OPEN, round.getStatus());
        verify(taskHistoryService).recordEvent(
                eq(task),
                eq(actor),
                eq(TaskHistory.EventType.REVIEW_ROUND_CREATED),
                any(java.util.Map.class)
        );
    }

    @Test
    void createNewRoundShouldRejectNonReviewer() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).reviewRequired(true).project(Project.builder().id(UUID.randomUUID()).build()).build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(reviewerRepository.existsByTaskIdAndUserId(eq(taskId), any(UUID.class))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.createNewRound(taskId, null));
    }

    @Test
    void completeRoundShouldRejectNonAssignee() {
        UUID roundId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).reviewRequired(true).project(Project.builder().id(UUID.randomUUID()).build()).build();
        TaskReviewRound round = TaskReviewRound.builder().id(roundId).task(task).status(TaskReviewRound.RoundStatus.OPEN).build();
        when(roundRepository.findById(roundId)).thenReturn(Optional.of(round));
        when(userService.getCurrentUser()).thenReturn(User.builder().id(UUID.randomUUID()).build());
        when(taskAssigneeService.isAssignee(eq(taskId), any(UUID.class))).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.completeRound(roundId));
    }

    @Test
    void completeRoundShouldMarkCompletedAndResetNonApprovedReviewers() {
        UUID roundId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).reviewRequired(true).project(Project.builder().id(UUID.randomUUID()).build()).build();
        TaskReviewRound round = TaskReviewRound.builder().id(roundId).task(task).status(TaskReviewRound.RoundStatus.OPEN).roundNumber(2).build();
        TaskReviewer approved = TaskReviewer.builder().status(TaskReviewer.ReviewStatus.APPROVED).build();
        TaskReviewer pending = TaskReviewer.builder().status(TaskReviewer.ReviewStatus.PENDING).build();
        when(roundRepository.findById(roundId)).thenReturn(Optional.of(round));
        when(userService.getCurrentUser()).thenReturn(User.builder().id(actorId).build());
        when(taskAssigneeService.isAssignee(taskId, actorId)).thenReturn(true);
        when(reviewerRepository.findByTaskId(taskId)).thenReturn(List.of(approved, pending));
        when(roundRepository.save(round)).thenReturn(round);

        TaskReviewRound completed = service.completeRound(roundId);

        assertEquals(TaskReviewRound.RoundStatus.COMPLETED, completed.getStatus());
        assertEquals(TaskReviewer.ReviewStatus.PENDING, pending.getStatus());
        verify(notificationService).notifyRoundRecheck(
                eq(task),
                any(User.class),
                eq(List.of(pending))
        );
    }

    @Test
    void completeRoundShouldRejectWithoutReviewers() {
        UUID roundId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).reviewRequired(true).project(Project.builder().id(UUID.randomUUID()).build()).build();
        TaskReviewRound round = TaskReviewRound.builder().id(roundId).task(task).status(TaskReviewRound.RoundStatus.OPEN).build();
        when(roundRepository.findById(roundId)).thenReturn(Optional.of(round));
        when(userService.getCurrentUser()).thenReturn(User.builder().id(actorId).build());
        when(taskAssigneeService.isAssignee(taskId, actorId)).thenReturn(true);
        when(reviewerRepository.findByTaskId(taskId)).thenReturn(List.of());

        assertThrows(InvalidStateException.class, () -> service.completeRound(roundId));
    }
}

