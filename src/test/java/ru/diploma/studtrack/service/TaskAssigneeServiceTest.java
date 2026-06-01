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
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskAssigneeRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAssigneeServiceTest {

    @Mock
    private TaskAssigneeRepository repository;
    @Mock
    private TaskService taskService;
    @Mock
    private UserService userService;
    @Mock
    private ProjectService projectService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private TaskHistoryService taskHistoryService;

    private TaskAssigneeService service;

    @BeforeEach
    void setUp() {
        service = new TaskAssigneeService(repository, taskService, userService, projectService, notificationService, taskHistoryService);
    }

    @Test
    void addAssigneeShouldThrowWhenDuplicate() {
        UUID taskId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .project(Project.builder().id(UUID.randomUUID()).owner(User.builder().id(ownerId).build()).build())
                .reviewers(new HashSet<>())
                .build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(User.builder().id(ownerId).build());
        when(projectService.isOwner(task.getProject().getId(), ownerId)).thenReturn(true);
        when(repository.existsByTaskIdAndUserId(taskId, ownerId)).thenReturn(false);
        when(repository.existsByTaskIdAndUserId(taskId, assigneeId)).thenReturn(true);

        assertThrows(AlreadyExistsException.class, () -> service.addAssignee(taskId, assigneeId));
    }

    @Test
    void addAssigneeShouldSaveAndRecordHistory() {
        UUID taskId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .project(Project.builder().id(UUID.randomUUID()).owner(User.builder().id(ownerId).build()).build())
                .reviewers(new HashSet<>())
                .build();
        User actor = User.builder().id(ownerId).build();
        User assignee = User.builder().id(assigneeId).lastName("Иванов").firstName("Иван").build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(projectService.isOwner(task.getProject().getId(), ownerId)).thenReturn(true);
        when(repository.existsByTaskIdAndUserId(taskId, ownerId)).thenReturn(false);
        when(repository.existsByTaskIdAndUserId(taskId, assigneeId)).thenReturn(false);
        when(userService.findById(assigneeId)).thenReturn(assignee);
        when(repository.save(any(TaskAssignee.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskAssignee result = service.addAssignee(taskId, assigneeId);

        assertEquals(assigneeId, result.getUser().getId());
        verify(notificationService).notifyTaskAssigned(assignee, actor, task);
        verify(taskHistoryService).recordEvent(
                task,
                actor,
                TaskHistory.EventType.ASSIGNEE_ADDED,
                Map.of("assigneeId", assignee.getId(), "assigneeName", assignee.getFullName())
        );
    }

    @Test
    void removeAssigneeShouldRejectLastAssigneeOnReview() {
        UUID taskId = UUID.randomUUID();
        UUID currentId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .status(Task.TaskStatus.REVIEW)
                .reviewRequired(true)
                .project(Project.builder().id(UUID.randomUUID()).owner(User.builder().id(currentId).build()).build())
                .build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(User.builder().id(currentId).build());
        when(projectService.isOwner(task.getProject().getId(), currentId)).thenReturn(true);
        when(repository.findByTaskId(taskId)).thenReturn(List.of(TaskAssignee.builder().user(User.builder().id(currentId).build()).build()));

        assertThrows(AccessDeniedException.class, () -> service.removeAssignee(taskId, currentId));
    }

    @Test
    void removeAssigneeShouldDeleteAndRecordEvent() {
        UUID taskId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Task task = Task.builder()
                .id(taskId)
                .status(Task.TaskStatus.IN_PROGRESS)
                .reviewRequired(false)
                .project(Project.builder().id(UUID.randomUUID()).owner(User.builder().id(ownerId).build()).build())
                .build();
        User actor = User.builder().id(ownerId).build();
        User assignee = User.builder().id(assigneeId).lastName("Петров").firstName("Петр").build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(projectService.isOwner(task.getProject().getId(), ownerId)).thenReturn(true);
        when(repository.findByTaskId(taskId)).thenReturn(List.of(
                TaskAssignee.builder().user(User.builder().id(assigneeId).build()).build(),
                TaskAssignee.builder().user(User.builder().id(UUID.randomUUID()).build()).build()
        ));
        when(userService.findById(assigneeId)).thenReturn(assignee);

        service.removeAssignee(taskId, assigneeId);

        verify(repository).deleteByTaskIdAndUserId(taskId, assigneeId);
        verify(taskHistoryService).recordEvent(
                task,
                actor,
                TaskHistory.EventType.ASSIGNEE_REMOVED,
                Map.of("assigneeId", assignee.getId(), "assigneeName", assignee.getFullName())
        );
    }

    @Test
    void isAssigneeShouldDelegateToRepository() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.existsByTaskIdAndUserId(taskId, userId)).thenReturn(true);
        assertEquals(true, service.isAssignee(taskId, userId));
    }
}

