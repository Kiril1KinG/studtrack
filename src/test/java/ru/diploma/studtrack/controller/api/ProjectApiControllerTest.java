package ru.diploma.studtrack.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.diploma.studtrack.dto.request.ProjectCreateRequest;
import ru.diploma.studtrack.dto.response.ProjectResponse;
import ru.diploma.studtrack.mapper.ProjectMapper;
import ru.diploma.studtrack.mapper.ProjectMemberMapper;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.service.ProjectService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectApiControllerTest {

    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectMemberMapper projectMemberMapper;

    private ProjectApiController controller;

    @BeforeEach
    void setUp() {
        controller = new ProjectApiController(projectService, projectMapper, projectMemberMapper);
    }

    @Test
    void getMyProjectsShouldReturnOk() {
        when(projectService.getMyProjects()).thenReturn(List.of());
        when(projectMapper.toResponseList(List.of())).thenReturn(List.of());

        var response = controller.getMyProjects();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody());
    }

    @Test
    void createProjectShouldReturnCreated() {
        UUID id = UUID.randomUUID();
        Project project = Project.builder().id(id).name("Proj").build();
        ProjectResponse dto = ProjectResponse.builder().id(id).name("Proj").build();
        when(projectService.create(anyString(), anyString())).thenReturn(project);
        when(projectMapper.toResponse(project)).thenReturn(dto);

        var response = controller.createProject(ProjectCreateRequest.builder().name("Proj").description("d").build());

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Proj", response.getBody().getName());
    }

    @Test
    void deleteProjectShouldReturnNoContent() {
        UUID id = UUID.randomUUID();
        var response = controller.deleteProject(id);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(projectService).delete(id);
    }

    @Test
    void getOwnedProjectsShouldReturnOk() {
        when(projectService.getOwnedProjects()).thenReturn(List.of());
        when(projectMapper.toResponseList(List.of())).thenReturn(List.of());
        var response = controller.getOwnedProjects();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getProjectShouldReturnOk() {
        UUID id = UUID.randomUUID();
        Project project = Project.builder().id(id).name("One").build();
        ProjectResponse dto = ProjectResponse.builder().id(id).name("One").build();
        when(projectService.findById(id)).thenReturn(project);
        when(projectMapper.toResponse(project)).thenReturn(dto);
        var response = controller.getProject(id);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("One", response.getBody().getName());
    }

    @Test
    void getMembersShouldReturnOk() {
        UUID projectId = UUID.randomUUID();
        when(projectService.getMembers(projectId)).thenReturn(List.of());
        when(projectMemberMapper.toResponseList(List.of())).thenReturn(List.of());
        var response = controller.getMembers(projectId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void addMemberShouldReturnCreated() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ProjectMember member = ProjectMember.builder().id(UUID.randomUUID()).build();
        when(projectService.addMember(projectId, userId)).thenReturn(member);
        when(projectMemberMapper.toResponse(member)).thenReturn(null);
        var request = ru.diploma.studtrack.dto.request.AddMemberRequest.builder().userId(userId).build();
        var response = controller.addMember(projectId, request);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void removeAndLeaveShouldReturnNoContent() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        assertEquals(HttpStatus.NO_CONTENT, controller.removeMember(projectId, userId).getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, controller.leaveProject(projectId).getStatusCode());
    }
}

