package ru.diploma.studtrack.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diploma.studtrack.dto.request.TaskCreateRequest;
import ru.diploma.studtrack.dto.request.TaskStatusUpdateRequest;
import ru.diploma.studtrack.dto.request.TaskUpdateRequest;
import ru.diploma.studtrack.dto.response.TaskResponse;
import ru.diploma.studtrack.mapper.TaskMapper;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.service.TaskService;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskMapper taskMapper;

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasksByProject(@PathVariable UUID projectId) {
        log.info("Запрос на получение задач проекта id: {}", projectId);
        List<Task> tasks = taskService.getTasksByProject(projectId);
        log.debug("Найдено {} задач", tasks.size());
        return ResponseEntity.ok(taskMapper.toResponseList(tasks));
    }

    @GetMapping("/my")
    public ResponseEntity<List<TaskResponse>> getMyTasks(@PathVariable UUID projectId) {
        log.info("Запрос на получение задач текущего пользователя в проекте id: {}", projectId);
        List<Task> tasks = taskService.getMyTasks(projectId);
        log.debug("Найдено {} задач пользователя", tasks.size());
        return ResponseEntity.ok(taskMapper.toResponseList(tasks));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID taskId) {
        log.info("Запрос на получение задачи id: {}", taskId);
        Task task = taskService.findById(taskId);
        log.debug("Задача найдена: {}", task.getTitle());
        return ResponseEntity.ok(taskMapper.toResponse(task));
    }

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(
            @PathVariable UUID projectId,
            @Valid @RequestBody TaskCreateRequest request) {
        log.info("Запрос на создание задачи в проекте {}: title='{}', priority={}, reviewRequired={}, assigneeId={}",
                projectId, request.getTitle(), request.getPriority(), request.isReviewRequired(), request.getAssigneeId());
        Task task = taskService.create(
                projectId,
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.isReviewRequired(),
                request.getAssigneeId(),
                request.getDeadline()
        );
        log.info("Задача создана с id: {}", task.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(taskMapper.toResponse(task));
    }

    @PutMapping("/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskUpdateRequest request) {
        log.info("Запрос на обновление задачи id: {}: title='{}', priority={}, assigneeId={}",
                taskId, request.getTitle(), request.getPriority(), request.getAssigneeId());
        Task task = taskService.update(
                taskId,
                request.getTitle(),
                request.getDescription(),
                request.getPriority(),
                request.getAssigneeId(),
                request.isReviewRequired(),
                request.getDeadline()
        );
        log.info("Задача обновлена: {}", task.getId());
        return ResponseEntity.ok(taskMapper.toResponse(task));
    }

    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskResponse> changeStatus(
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskStatusUpdateRequest request) {
        log.info("Запрос на изменение статуса задачи id: {} -> {}", taskId, request.getStatus());
        Task task = taskService.changeStatus(taskId, request.getStatus());
        log.info("Статус задачи изменён: {}", task.getStatus());
        return ResponseEntity.ok(taskMapper.toResponse(task));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID taskId) {
        log.info("Запрос на удаление задачи id: {}", taskId);
        taskService.delete(taskId);
        log.info("Задача удалена: {}", taskId);
        return ResponseEntity.noContent().build();
    }
}