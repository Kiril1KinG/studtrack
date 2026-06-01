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
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.TaskReviewRound;
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
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
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

    @Test
    void createReviewRoundShouldSetFlashErrorWhenCannotStart() {
        UUID taskId = UUID.randomUUID();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        when(reviewRoundService.canStartNewRound(taskId)).thenReturn(false);

        String view = controller.createReviewRound(taskId, "sum", redirect);
        assertEquals("redirect:/tasks/" + taskId, view);
        assertEquals(
                "Нельзя начать новый раунд: в предыдущем раунде есть незакрытые замечания.",
                redirect.getFlashAttributes().get("errorMessage")
        );
    }

    @Test
    void updateTaskShouldRedirectAndSetFriendlyError() {
        UUID taskId = UUID.randomUUID();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        when(taskService.update(eq(taskId), anyString(), any(), any(), any(), any(Boolean.class), any()))
                .thenThrow(new IllegalArgumentException("bad"));
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");

        String view = controller.updateTask(
                taskId, "t", "d", Task.Priority.HIGH, null, false, null, redirect
        );
        assertEquals("redirect:/tasks/" + taskId, view);
        assertEquals("friendly", redirect.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void submitReviewShouldReturnReviewerFragmentOnSuccess() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(projectId).build()).build();
        User currentUser = User.builder().id(currentUserId).build();
        Model model = new ExtendedModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(projectService.isOwner(projectId, currentUserId)).thenReturn(false);
        when(taskAssigneeService.isAssignee(taskId, currentUserId)).thenReturn(true);
        when(reviewRoundService.isLastRoundCompleted(taskId)).thenReturn(true);
        when(taskReviewerService.getReviewersByTask(taskId)).thenReturn(List.of());

        String view = controller.submitReview(taskId, reviewerId, TaskReviewer.ReviewStatus.APPROVED, "ok", model);
        assertEquals("fragments/task-reviewers :: reviewerList", view);
        verify(taskReviewerService).submitReview(taskId, reviewerId, TaskReviewer.ReviewStatus.APPROVED, "ok");
    }

    @Test
    void myTasksShouldApplyFiltersAndReturnView() {
        UUID projectId = UUID.randomUUID();
        Task overdue = Task.builder()
                .id(UUID.randomUUID())
                .status(Task.TaskStatus.IN_PROGRESS)
                .priority(Task.Priority.HIGH)
                .deadline(LocalDate.now().minusDays(1))
                .project(Project.builder().id(projectId).build())
                .build();
        Task done = Task.builder()
                .id(UUID.randomUUID())
                .status(Task.TaskStatus.DONE)
                .priority(Task.Priority.HIGH)
                .deadline(LocalDate.now().minusDays(1))
                .project(Project.builder().id(projectId).build())
                .build();
        Model model = new ExtendedModelMap();
        when(taskService.getAssignedToMe()).thenReturn(List.of(overdue, done));
        when(taskService.getPendingReviewsForMe()).thenReturn(List.of());

        String view = controller.myTasks(Task.Priority.HIGH, "overdue", projectId, model);

        assertEquals("tasks/my", view);
        assertEquals(1, ((List<?>) model.getAttribute("assignedTasks")).size());
        assertEquals("Мои задачи", model.getAttribute("pageTitle"));
    }

    @Test
    void createTaskShouldReturnKanbanFragment() {
        UUID projectId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).build();
        Model model = new ExtendedModelMap();
        ru.diploma.studtrack.dto.request.TaskCreateRequest request = ru.diploma.studtrack.dto.request.TaskCreateRequest.builder()
                .title("T")
                .description("D")
                .priority(Task.Priority.LOW)
                .reviewRequired(false)
                .build();
        when(projectService.findById(projectId)).thenReturn(project);
        when(taskService.getTasksByProject(projectId)).thenReturn(List.of());
        when(taskService.getReviewStateByTaskId(List.of())).thenReturn(Map.of());
        when(taskService.getReviewStatsByTaskId(List.of())).thenReturn(Map.of());

        String view = controller.createTask(projectId, request, model);

        assertEquals("projects/fragments :: kanbanBoard", view);
        verify(taskService).create(projectId, "T", "D", Task.Priority.LOW, false, null, null);
    }

    @Test
    void viewTaskShouldReturnDetailWithModelData() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        User currentUser = User.builder().id(currentUserId).build();
        Task task = Task.builder().id(taskId).title("Task").project(Project.builder().id(projectId).build()).build();
        TaskHistory history = TaskHistory.builder().id(UUID.randomUUID()).task(task).build();
        Model model = new ExtendedModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(projectService.isOwner(projectId, currentUserId)).thenReturn(true);
        when(taskReviewerService.getReviewersByTask(taskId)).thenReturn(List.of());
        when(taskAssigneeService.getAssigneesByTask(taskId)).thenReturn(List.of());
        when(commentService.getByTask(taskId)).thenReturn(List.of());
        when(reviewRoundService.getRoundsForView(taskId)).thenReturn(List.of(TaskReviewRound.builder().build()));
        when(taskHistoryService.getByTask(taskId)).thenReturn(List.of(history));
        when(taskHistoryService.toHumanMessage(history)).thenReturn("msg");
        when(taskAttachmentService.getTaskArtifacts(taskId)).thenReturn(List.of());
        when(projectService.getMembers(projectId)).thenReturn(List.of());
        when(taskAssigneeService.isAssignee(taskId, currentUserId)).thenReturn(false);
        when(reviewRoundService.isLastRoundCompleted(taskId)).thenReturn(true);

        String view = controller.viewTask(taskId, model);

        assertEquals("tasks/detail", view);
        assertEquals(task, model.getAttribute("task"));
        assertEquals("Задача: Task", model.getAttribute("pageTitle"));
    }

    @Test
    void getTaskHistoryShouldReturnFragment() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        TaskHistory history = TaskHistory.builder().id(UUID.randomUUID()).task(task).build();
        Model model = new ExtendedModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        when(taskHistoryService.getByTask(taskId)).thenReturn(List.of(history));
        when(taskHistoryService.toHumanMessage(history)).thenReturn("msg");

        String view = controller.getTaskHistory(taskId, model);

        assertEquals("fragments/task-history :: historyList", view);
        assertEquals(List.of(history), model.getAttribute("historyEntries"));
    }

    @Test
    void assignTaskShouldAddSelectedAssigneeForOwner() {
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(projectId).build()).build();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(projectService.isOwner(eq(projectId), any())).thenReturn(true);

        String view = controller.assignTask(taskId, assigneeId, redirect);

        assertEquals("redirect:/tasks/" + taskId, view);
        verify(taskAssigneeService).addAssignee(taskId, assigneeId);
    }

    @Test
    void assignTaskShouldSetFlashWhenOwnerDoesNotProvideAssignee() {
        UUID taskId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(projectId).build()).build();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(projectService.isOwner(eq(projectId), any())).thenReturn(true);

        String view = controller.assignTask(taskId, null, redirect);

        assertEquals("redirect:/tasks/" + taskId, view);
        assertEquals("Укажите исполнителя для назначения", redirect.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void removeAssigneeShouldPutFriendlyErrorOnException() {
        UUID taskId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(projectId).build()).build();
        User current = User.builder().id(currentUserId).build();
        Model model = new ExtendedModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        doThrow(new IllegalArgumentException("bad")).when(taskAssigneeService).removeAssignee(taskId, assigneeId);
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");
        when(userService.getCurrentUser()).thenReturn(current);
        when(taskAssigneeService.getAssigneesByTask(taskId)).thenReturn(List.of(TaskAssignee.builder().task(task).user(current).build()));
        when(projectService.isOwner(projectId, currentUserId)).thenReturn(false);
        when(taskAssigneeService.isAssignee(taskId, currentUserId)).thenReturn(true);

        String view = controller.removeAssignee(taskId, assigneeId, model);

        assertEquals("fragments/task-assignees :: assigneeList", view);
        assertEquals("friendly", model.getAttribute("assigneeErrorMessage"));
    }

    @Test
    void removeReviewerShouldPutFriendlyErrorOnException() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(projectId).build()).build();
        User current = User.builder().id(currentUserId).build();
        Model model = new ExtendedModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        doThrow(new IllegalArgumentException("bad")).when(taskReviewerService).removeReviewer(taskId, reviewerId);
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");
        when(taskReviewerService.getReviewersByTask(taskId)).thenReturn(List.of());
        when(userService.getCurrentUser()).thenReturn(current);
        when(taskAssigneeService.isAssignee(taskId, currentUserId)).thenReturn(false);
        when(projectService.isOwner(projectId, currentUserId)).thenReturn(false);
        when(reviewRoundService.isLastRoundCompleted(taskId)).thenReturn(false);

        String view = controller.removeReviewer(taskId, reviewerId, model);

        assertEquals("fragments/task-reviewers :: reviewerList", view);
        assertEquals("friendly", model.getAttribute("reviewerErrorMessage"));
    }
}

