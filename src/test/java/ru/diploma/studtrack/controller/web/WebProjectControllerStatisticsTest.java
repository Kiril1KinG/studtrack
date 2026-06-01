package ru.diploma.studtrack.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import ru.diploma.studtrack.dto.request.ProjectStatisticsFilter;
import ru.diploma.studtrack.dto.request.TaskCreateRequest;
import ru.diploma.studtrack.dto.response.ProjectStatisticsResponse;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.ProjectStatisticsService;
import ru.diploma.studtrack.service.AttachmentHistoryValueService;
import ru.diploma.studtrack.service.TaskAttachmentService;
import ru.diploma.studtrack.service.TaskHistoryService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.UserService;
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebProjectControllerStatisticsTest {

    @Mock
    private ProjectService projectService;
    @Mock
    private TaskService taskService;
    @Mock
    private UserService userService;
    @Mock
    private TaskAttachmentService taskAttachmentService;
    @Mock
    private TaskHistoryService taskHistoryService;
    @Mock
    private ProjectStatisticsService projectStatisticsService;
    @Mock
    private AttachmentHistoryValueService attachmentHistoryValueService;
    @Mock
    private WebErrorMessageService webErrorMessageService;

    private WebProjectController controller;
    private UUID projectId;
    private UUID memberId;
    private Project project;
    private User currentUser;
    private ProjectStatisticsResponse statistics;

    @BeforeEach
    void setUp() {
        controller = new WebProjectController(
                projectService,
                taskService,
                userService,
                taskAttachmentService,
                taskHistoryService,
                projectStatisticsService,
                attachmentHistoryValueService,
                webErrorMessageService
        );

        projectId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        currentUser = User.builder().id(UUID.randomUUID()).lastName("Owner").firstName("Test").email("o@test").build();
        project = Project.builder().id(projectId).name("Project").owner(currentUser).build();
        statistics = new ProjectStatisticsResponse(
                ProjectStatisticsFilter.Period.DAYS_30,
                memberId,
                LocalDateTime.now(),
                new ProjectStatisticsResponse.ProjectKpi(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
                new ProjectStatisticsResponse.DurationKpi(0, 0, null, null, null, null, null, null, null),
                List.of()
        );
    }

    @Test
    void getStatisticsShouldReturnFragmentWithFilterModel() {
        Model model = new ExtendedModelMap();
        when(projectService.findById(projectId)).thenReturn(project);
        when(projectService.getMembers(projectId)).thenReturn(List.of(ProjectMember.builder().project(project).user(currentUser).build()));
        when(projectStatisticsService.getProjectStatistics(eq(projectId), any(ProjectStatisticsFilter.class))).thenReturn(statistics);

        String view = controller.getStatistics(projectId, "30d", memberId, model);

        assertEquals("projects/fragments :: statisticsTab", view);
        assertEquals("30d", model.getAttribute("statisticsPeriod"));
        assertEquals(memberId, model.getAttribute("statisticsMemberId"));
        assertEquals(statistics, model.getAttribute("statistics"));
    }

    @Test
    void viewProjectShouldWireStatisticsTabModel() {
        Model model = new ExtendedModelMap();
        when(projectService.findById(projectId)).thenReturn(project);
        when(taskService.getTasksByProject(projectId)).thenReturn(List.of());
        when(taskService.getReviewStateByTaskId(List.of())).thenReturn(Map.of());
        when(taskService.getReviewStatsByTaskId(List.of())).thenReturn(Map.of());
        when(projectService.getMembers(projectId)).thenReturn(List.of(ProjectMember.builder().project(project).user(currentUser).build()));
        when(projectStatisticsService.getProjectStatistics(eq(projectId), any(ProjectStatisticsFilter.class))).thenReturn(statistics);
        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(projectService.isOwner(projectId, currentUser.getId())).thenReturn(true);
        when(taskAttachmentService.getProjectArtifacts(projectId, "newest")).thenReturn(List.of());

        String view = controller.viewProject(projectId, "statistics", "newest", "30d", memberId, model);

        assertEquals("projects/detail", view);
        assertEquals("statistics", model.getAttribute("activeTab"));
        assertEquals("30d", model.getAttribute("statisticsPeriod"));
        assertEquals(memberId, model.getAttribute("statisticsMemberId"));
        assertEquals(statistics, model.getAttribute("statistics"));
    }

    @Test
    void listProjectsShouldReturnListView() {
        Model model = new ExtendedModelMap();
        when(projectService.getMyProjects()).thenReturn(List.of(project));

        String view = controller.listProjects(model);

        assertEquals("projects/list", view);
        assertEquals("Мои проекты", model.getAttribute("pageTitle"));
        assertEquals(List.of(project), model.getAttribute("projects"));
    }

    @Test
    void createProjectFormShouldReturnCreateView() {
        Model model = new ExtendedModelMap();

        String view = controller.createProjectForm(model);

        assertEquals("projects/create", view);
        assertEquals("Создать проект", model.getAttribute("pageTitle"));
    }

    @Test
    void createProjectShouldRedirectToCreatedProject() {
        when(projectService.create("N", "D")).thenReturn(project);

        String view = controller.createProject("N", "D");

        assertEquals("redirect:/projects/" + projectId, view);
    }

    @Test
    void getKanbanBoardShouldReturnFragment() {
        Model model = new ExtendedModelMap();
        when(projectService.findById(projectId)).thenReturn(project);
        when(taskService.getTasksByProject(projectId)).thenReturn(List.of());
        when(taskService.getReviewStateByTaskId(List.of())).thenReturn(Map.of());
        when(taskService.getReviewStatsByTaskId(List.of())).thenReturn(Map.of());

        String view = controller.getKanbanBoard(projectId, model);

        assertEquals("projects/fragments :: kanbanBoard", view);
        verify(projectService).checkMembership(projectId);
        assertEquals(project, model.getAttribute("project"));
    }

    @Test
    void getRepositoryShouldReturnRepositoryTabFragment() {
        Model model = new ExtendedModelMap();
        when(projectService.findById(projectId)).thenReturn(project);
        when(taskAttachmentService.getProjectArtifacts(projectId, "type")).thenReturn(List.of());
        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(projectService.isOwner(projectId, currentUser.getId())).thenReturn(true);

        String view = controller.getRepository(projectId, "type", model);

        assertEquals("projects/fragments :: repositoryTab", view);
        assertEquals("type", model.getAttribute("repositorySort"));
        assertEquals(currentUser, model.getAttribute("currentUser"));
        assertEquals(true, model.getAttribute("isOwner"));
    }

    @Test
    void deleteRepositoryArtifactShouldRedirectToRepositoryTab() {
        UUID attachmentId = UUID.randomUUID();
        Task task = Task.builder().id(UUID.randomUUID()).project(project).build();
        TaskAttachment attachment = TaskAttachment.builder()
                .id(attachmentId)
                .task(task)
                .build();
        when(taskAttachmentService.findById(attachmentId)).thenReturn(attachment);
        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(attachmentHistoryValueService.historyValueFor(attachment)).thenReturn("FILE::a.txt");

        String view = controller.deleteRepositoryArtifact(projectId, attachmentId, "oldest");

        assertEquals("redirect:/projects/" + projectId + "?tab=repository&sort=oldest", view);
        verify(taskAttachmentService).deleteAttachment(attachmentId);
    }

    @Test
    void createTaskShouldReturnKanbanBoardFragment() {
        Model model = new ExtendedModelMap();
        TaskCreateRequest request = TaskCreateRequest.builder()
                .title("Task")
                .description("Desc")
                .priority(Task.Priority.MEDIUM)
                .reviewRequired(false)
                .build();
        when(projectService.findById(projectId)).thenReturn(project);
        when(taskService.getTasksByProject(projectId)).thenReturn(List.of());
        when(taskService.getReviewStateByTaskId(List.of())).thenReturn(Map.of());
        when(taskService.getReviewStatsByTaskId(List.of())).thenReturn(Map.of());

        String view = controller.createTask(projectId, request, model);

        assertEquals("projects/fragments :: kanbanBoard", view);
        verify(taskService).create(projectId, "Task", "Desc", Task.Priority.MEDIUM, false, null, null);
    }

    @Test
    void updateProjectShouldAddFlashErrorWhenServiceThrows() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("boom"))
                .when(projectService).update(projectId, "N", "D");
        when(webErrorMessageService.resolve(any(), any())).thenReturn("friendly");

        String view = controller.updateProject(projectId, "N", "D", redirect);

        assertEquals("redirect:/projects/" + projectId + "?tab=settings", view);
        assertEquals("friendly", redirect.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void addMemberShouldRedirectToMembersTab() {
        User invited = User.builder().id(UUID.randomUUID()).email("u@test").build();
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        when(userService.findByEmail("u@test")).thenReturn(invited);

        String view = controller.addMember(projectId, "u@test", redirect);

        assertEquals("redirect:/projects/" + projectId + "?tab=members", view);
        verify(projectService).addMember(projectId, invited.getId());
    }

    @Test
    void leaveProjectShouldRedirectToProjectsOnSuccess() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();

        String view = controller.leaveProject(projectId, redirect);

        assertEquals("redirect:/projects", view);
        verify(projectService).leaveProject(projectId);
    }
}
