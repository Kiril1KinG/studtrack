package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.ChangeRequestRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRequestServiceTest {

    @Mock private ChangeRequestRepository repository;
    @Mock private TaskReviewRoundService roundService;
    @Mock private TaskService taskService;
    @Mock private TaskAssigneeService taskAssigneeService;
    @Mock private UserService userService;
    @Mock private ProjectService projectService;
    @Mock private NotificationService notificationService;

    private ChangeRequestService service;

    @BeforeEach
    void setUp() {
        service = new ChangeRequestService(
                repository,
                roundService,
                taskService,
                taskAssigneeService,
                userService,
                projectService,
                notificationService
        );
    }

    @Test
    void createShouldPersistAndNotify() {
        UUID taskId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).reviewRequired(true).project(Project.builder().id(UUID.randomUUID()).build()).build();
        TaskReviewRound round = TaskReviewRound.builder().id(roundId).task(task).status(TaskReviewRound.RoundStatus.OPEN).build();
        User actor = User.builder().id(UUID.randomUUID()).build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(roundService.findById(roundId)).thenReturn(round);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(repository.save(any(ChangeRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ChangeRequest created = service.create(taskId, roundId, "fix");

        assertEquals("fix", created.getContent());
        verify(notificationService).notifyChangeRequestCreated(created, actor);
    }

    @Test
    void createShouldRejectWhenReviewDisabled() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).reviewRequired(false).project(Project.builder().id(UUID.randomUUID()).build()).build();
        when(taskService.findById(taskId)).thenReturn(task);

        assertThrows(InvalidStateException.class, () -> service.create(taskId, UUID.randomUUID(), "text"));
    }

    @Test
    void markResolvedShouldRequireAssigneeAndOpenRound() {
        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        TaskReviewRound round = TaskReviewRound.builder().status(TaskReviewRound.RoundStatus.OPEN).build();
        ChangeRequest cr = ChangeRequest.builder().id(id).task(task).round(round).build();
        when(repository.findById(id)).thenReturn(Optional.of(cr));
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(taskAssigneeService.isAssignee(taskId, userId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.markAsResolved(id));
    }

    @Test
    void markOpenShouldUpdateStatusWhenAssignee() {
        UUID id = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        TaskReviewRound round = TaskReviewRound.builder().status(TaskReviewRound.RoundStatus.OPEN).build();
        ChangeRequest cr = ChangeRequest.builder().id(id).task(task).round(round).status(ChangeRequest.ChangeRequestStatus.RESOLVED).build();
        when(repository.findById(id)).thenReturn(Optional.of(cr));
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(taskAssigneeService.isAssignee(taskId, userId)).thenReturn(true);
        when(repository.save(cr)).thenReturn(cr);

        ChangeRequest updated = service.markAsOpen(id);
        assertEquals(ChangeRequest.ChangeRequestStatus.OPEN, updated.getStatus());
    }

    @Test
    void deleteShouldAllowOnlyAuthor() {
        UUID id = UUID.randomUUID();
        ChangeRequest cr = ChangeRequest.builder().id(id).author(User.builder().id(UUID.randomUUID()).build()).build();
        when(repository.findById(id)).thenReturn(Optional.of(cr));
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> service.delete(id));
    }

    @Test
    void queryMethodsShouldDelegate() {
        UUID taskId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        when(repository.findByTaskId(taskId)).thenReturn(List.of());
        when(repository.findByRoundId(roundId)).thenReturn(List.of());
        when(repository.findByRoundIdAndStatus(roundId, ChangeRequest.ChangeRequestStatus.OPEN)).thenReturn(List.of());
        assertEquals(List.of(), service.getByTask(taskId));
        assertEquals(List.of(), service.getByRound(roundId));
        assertEquals(List.of(), service.getOpenByRound(roundId));
    }
}

