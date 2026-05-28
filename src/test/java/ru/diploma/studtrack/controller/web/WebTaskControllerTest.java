package ru.diploma.studtrack.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.ChangeRequestService;
import ru.diploma.studtrack.service.CommentService;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskAssigneeService;
import ru.diploma.studtrack.service.TaskAttachmentService;
import ru.diploma.studtrack.service.TaskHistoryService;
import ru.diploma.studtrack.service.TaskReviewRoundService;
import ru.diploma.studtrack.service.TaskReviewerService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.UserService;
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebTaskControllerTest {

    @Mock private TaskService taskService;
    @Mock private TaskAssigneeService taskAssigneeService;
    @Mock private TaskReviewerService taskReviewerService;
    @Mock private CommentService commentService;
    @Mock private UserService userService;
    @Mock private ProjectService projectService;
    @Mock private TaskReviewRoundService reviewRoundService;
    @Mock private ChangeRequestService changeRequestService;
    @Mock private TaskHistoryService taskHistoryService;
    @Mock private TaskAttachmentService taskAttachmentService;
    @Mock private WebErrorMessageService webErrorMessageService;

    private WebTaskController controller;

    @BeforeEach
    void setUp() {
        controller = new WebTaskController(
                taskService,
                taskAssigneeService,
                taskReviewerService,
                commentService,
                userService,
                projectService,
                reviewRoundService,
                changeRequestService,
                taskHistoryService,
                taskAttachmentService,
                webErrorMessageService
        );
    }

    @Test
    void changeStatusShouldReturnKanbanFragmentWhenReturnToKanban() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(projectId).build()).build();
        when(taskService.changeStatus(taskId, Task.TaskStatus.IN_PROGRESS)).thenReturn(task);
        when(projectService.findById(projectId)).thenReturn(Project.builder().id(projectId).build());
        when(taskService.getTasksByProject(projectId)).thenReturn(List.of());
        when(taskService.getReviewStateByTaskId(List.of())).thenReturn(Map.of());
        when(taskService.getReviewStatsByTaskId(List.of())).thenReturn(Map.of());

        String view = controller.changeStatus(
                taskId,
                Task.TaskStatus.IN_PROGRESS,
                "kanban",
                new RedirectAttributesModelMap(),
                new ExtendedModelMap()
        );

        assertEquals("projects/fragments :: kanbanBoard", view);
    }

    @Test
    void changeStatusShouldRedirectAndSetFlashErrorOnFailure() {
        UUID taskId = UUID.randomUUID();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        when(taskService.changeStatus(taskId, Task.TaskStatus.DONE)).thenThrow(new IllegalArgumentException("bad"));
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");

        String view = controller.changeStatus(taskId, Task.TaskStatus.DONE, "detail", redirect, new ExtendedModelMap());

        assertEquals("redirect:/tasks/" + taskId, view);
        assertEquals("friendly", redirect.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void addAssigneeShouldReturnFragmentAndExposeErrorMessage() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(projectId).build()).build();
        User current = User.builder().id(UUID.randomUUID()).build();
        Model model = new ExtendedModelMap();

        when(taskService.findById(taskId)).thenReturn(task);
        when(taskAssigneeService.addAssignee(taskId, assigneeId)).thenThrow(new IllegalArgumentException("bad"));
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");
        when(userService.getCurrentUser()).thenReturn(current);
        when(taskAssigneeService.getAssigneesByTask(taskId)).thenReturn(List.of());
        when(projectService.isOwner(projectId, current.getId())).thenReturn(false);
        when(taskAssigneeService.isAssignee(taskId, current.getId())).thenReturn(false);

        String view = controller.addAssignee(taskId, assigneeId, model);

        assertEquals("fragments/task-assignees :: assigneeList", view);
        assertEquals("friendly", model.getAttribute("assigneeErrorMessage"));
    }
}

