package ru.diploma.studtrack.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.diploma.studtrack.dto.request.TaskCreateRequest;
import ru.diploma.studtrack.dto.request.TaskStatusUpdateRequest;
import ru.diploma.studtrack.dto.request.TaskUpdateRequest;
import ru.diploma.studtrack.dto.response.TaskResponse;
import ru.diploma.studtrack.mapper.TaskMapper;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.service.TaskService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskApiControllerTest {

    @Mock
    private TaskService taskService;
    @Mock
    private TaskMapper taskMapper;

    private TaskApiController controller;

    @BeforeEach
    void setUp() {
        controller = new TaskApiController(taskService, taskMapper);
    }

    @Test
    void getTasksByProjectShouldReturnOk() {
        UUID projectId = UUID.randomUUID();
        when(taskService.getTasksByProject(projectId)).thenReturn(List.of());
        when(taskMapper.toResponseList(List.of())).thenReturn(List.of());

        var response = controller.getTasksByProject(projectId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(List.of(), response.getBody());
    }

    @Test
    void createTaskShouldReturnCreated() {
        UUID projectId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).title("Task").build();
        TaskResponse dto = TaskResponse.builder().id(taskId).title("Task").build();
        when(taskService.create(any(), any(), any(), any(), any(Boolean.class), any(), any())).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(dto);

        TaskCreateRequest request = TaskCreateRequest.builder().title("Task").priority(Task.Priority.MEDIUM).build();
        var response = controller.createTask(projectId, request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("Task", response.getBody().getTitle());
    }

    @Test
    void patchStatusShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).title("Task").build();
        TaskResponse dto = TaskResponse.builder().id(taskId).title("Task").build();
        when(taskService.changeStatus(taskId, Task.TaskStatus.DONE)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(dto);

        var response = controller.changeStatus(taskId, TaskStatusUpdateRequest.builder().status(Task.TaskStatus.DONE).build());
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void deleteTaskShouldReturnNoContent() {
        UUID taskId = UUID.randomUUID();
        var response = controller.deleteTask(taskId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(taskService).delete(taskId);
    }

    @Test
    void getMyTasksShouldReturnOk() {
        UUID projectId = UUID.randomUUID();
        when(taskService.getMyTasks(projectId)).thenReturn(List.of());
        when(taskMapper.toResponseList(List.of())).thenReturn(List.of());
        var response = controller.getMyTasks(projectId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getTaskShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).title("One").build();
        TaskResponse dto = TaskResponse.builder().id(taskId).title("One").build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(dto);
        var response = controller.getTask(taskId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("One", response.getBody().getTitle());
    }

    @Test
    void updateTaskShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).title("Updated").priority(Task.Priority.HIGH).build();
        TaskResponse dto = TaskResponse.builder().id(taskId).title("Updated").build();
        when(taskService.update(any(), any(), any(), any(), any(), any(Boolean.class), any())).thenReturn(task);
        when(taskMapper.toResponse(task)).thenReturn(dto);
        TaskUpdateRequest request = TaskUpdateRequest.builder()
                .title("Updated")
                .priority(Task.Priority.HIGH)
                .reviewRequired(false)
                .build();
        var response = controller.updateTask(taskId, request);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Updated", response.getBody().getTitle());
    }
}

