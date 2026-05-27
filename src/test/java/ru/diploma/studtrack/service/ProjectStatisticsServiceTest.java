package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.dto.request.ProjectStatisticsFilter;
import ru.diploma.studtrack.dto.response.ProjectStatisticsResponse;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.ChangeRequestRepository;
import ru.diploma.studtrack.repository.TaskHistoryRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectStatisticsServiceTest {

    @Mock
    private TaskService taskService;
    @Mock
    private ProjectService projectService;
    @Mock
    private TaskHistoryRepository taskHistoryRepository;
    @Mock
    private ChangeRequestRepository changeRequestRepository;

    private ProjectStatisticsService projectStatisticsService;
    private UUID projectId;
    private UUID memberId;

    @BeforeEach
    void setUp() {
        projectStatisticsService = new ProjectStatisticsService(
                taskService,
                projectService,
                taskHistoryRepository,
                changeRequestRepository
        );
        projectId = UUID.randomUUID();
        memberId = UUID.randomUUID();
    }

    @Test
    void doneOnlyDurationsAndOpenAgeMustBeCalculatedSeparately() {
        User memberUser = User.builder().id(memberId).lastName("One").firstName("Student").email("s1@test").build();
        Project project = Project.builder().id(projectId).name("P").build();
        ProjectMember member = ProjectMember.builder().project(project).user(memberUser).build();

        Task doneTask = Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .title("Done")
                .status(Task.TaskStatus.DONE)
                .createdAt(LocalDateTime.now().minusHours(20))
                .assignees(new java.util.HashSet<>())
                .reviewers(new java.util.HashSet<>())
                .build();
        doneTask.getAssignees().add(TaskAssignee.builder().task(doneTask).user(memberUser).build());

        Task backlogTask = Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .title("Backlog")
                .status(Task.TaskStatus.BACKLOG)
                .createdAt(LocalDateTime.now().minusHours(50))
                .assignees(new java.util.HashSet<>())
                .reviewers(new java.util.HashSet<>())
                .build();
        backlogTask.getAssignees().add(TaskAssignee.builder().task(backlogTask).user(memberUser).build());

        when(taskService.getTasksByProject(projectId)).thenReturn(List.of(doneTask, backlogTask));
        when(projectService.getMembers(projectId)).thenReturn(List.of(member));
        when(changeRequestRepository.findByTaskIdAndStatus(ArgumentMatchers.any(), ArgumentMatchers.eq(ChangeRequest.ChangeRequestStatus.OPEN)))
                .thenReturn(List.of());

        TaskHistory inProgress = TaskHistory.builder()
                .task(doneTask)
                .eventType(TaskHistory.EventType.TASK_STATUS_CHANGED)
                .detailsJson("{\"newStatus\":\"IN_PROGRESS\"}")
                .createdAt(LocalDateTime.now().minusHours(18))
                .build();
        TaskHistory done = TaskHistory.builder()
                .task(doneTask)
                .eventType(TaskHistory.EventType.TASK_STATUS_CHANGED)
                .detailsJson("{\"newStatus\":\"DONE\"}")
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
        when(taskHistoryRepository.findStatusHistoryByProject(projectId, TaskHistory.EventType.TASK_STATUS_CHANGED))
                .thenReturn(List.of(inProgress, done));

        ProjectStatisticsResponse response = projectStatisticsService.getProjectStatistics(
                projectId,
                ProjectStatisticsFilter.of("all", null)
        );

        assertEquals(1, response.duration().leadSampleSize());
        assertEquals(1, response.duration().cycleSampleSize());
        assertNotNull(response.duration().avgLeadHours());
        assertNotNull(response.duration().avgCycleHours());
        assertNotNull(response.duration().avgOpenAgeHours());
        assertEquals(1, response.kpi().openTasks());
    }

    @Test
    void cycleMustBeNullWhenNoInProgressEvent() {
        User memberUser = User.builder().id(memberId).lastName("One").firstName("Student").email("s1@test").build();
        Project project = Project.builder().id(projectId).name("P").build();
        ProjectMember member = ProjectMember.builder().project(project).user(memberUser).build();

        Task doneTask = Task.builder()
                .id(UUID.randomUUID())
                .project(project)
                .title("Done")
                .status(Task.TaskStatus.DONE)
                .createdAt(LocalDateTime.now().minusHours(12))
                .assignees(new java.util.HashSet<>())
                .reviewers(new java.util.HashSet<>())
                .build();
        doneTask.getAssignees().add(TaskAssignee.builder().task(doneTask).user(memberUser).build());

        when(taskService.getTasksByProject(projectId)).thenReturn(List.of(doneTask));
        when(projectService.getMembers(projectId)).thenReturn(List.of(member));
        when(changeRequestRepository.findByTaskIdAndStatus(ArgumentMatchers.any(), ArgumentMatchers.eq(ChangeRequest.ChangeRequestStatus.OPEN)))
                .thenReturn(List.of());

        TaskHistory done = TaskHistory.builder()
                .task(doneTask)
                .eventType(TaskHistory.EventType.TASK_STATUS_CHANGED)
                .detailsJson("{\"newStatus\":\"DONE\"}")
                .createdAt(LocalDateTime.now().minusHours(2))
                .build();
        when(taskHistoryRepository.findStatusHistoryByProject(projectId, TaskHistory.EventType.TASK_STATUS_CHANGED))
                .thenReturn(List.of(done));

        ProjectStatisticsResponse response = projectStatisticsService.getProjectStatistics(
                projectId,
                ProjectStatisticsFilter.of("all", null)
        );

        assertEquals(1, response.duration().leadSampleSize());
        assertEquals(0, response.duration().cycleSampleSize());
        assertNull(response.duration().avgCycleHours());
    }
}
