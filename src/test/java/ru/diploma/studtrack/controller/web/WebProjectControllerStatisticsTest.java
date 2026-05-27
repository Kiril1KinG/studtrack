package ru.diploma.studtrack.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import ru.diploma.studtrack.dto.request.ProjectStatisticsFilter;
import ru.diploma.studtrack.dto.response.ProjectStatisticsResponse;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.model.Task;
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
        currentUser = User.builder().id(UUID.randomUUID()).fullName("Owner").email("o@test").build();
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
}
