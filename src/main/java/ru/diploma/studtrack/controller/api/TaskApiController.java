package ru.diploma.studtrack.controller.api;

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
/**
 * Предоставляет REST-эндпоинты для управления задачами внутри проекта.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@RequiredArgsConstructor
public class TaskApiController {

    /**
     * Выполняет бизнес-логику задач.
     */
    private final TaskService taskService;
    /**
     * Преобразует сущности задач в DTO-ответы API.
     */
    private final TaskMapper taskMapper;

    /**
     * Возвращает все задачи проекта.
     *
     * @param projectId идентификатор проекта
     * @return список задач
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getTasksByProject(@PathVariable UUID projectId) {
        log.info("Запрос на получение задач проекта id: {}", projectId);
        List<Task> tasks = taskService.getTasksByProject(projectId);
        log.debug("Найдено {} задач", tasks.size());
        return ResponseEntity.ok(taskMapper.toResponseList(tasks));
    }

    /**
     * Возвращает задачи проекта, назначенные текущему пользователю.
     *
     * @param projectId идентификатор проекта
     * @return список задач пользователя
     */
    @GetMapping("/my")
    public ResponseEntity<List<TaskResponse>> getMyTasks(@PathVariable UUID projectId) {
        log.info("Запрос на получение задач текущего пользователя в проекте id: {}", projectId);
        List<Task> tasks = taskService.getMyTasks(projectId);
        log.debug("Найдено {} задач пользователя", tasks.size());
        return ResponseEntity.ok(taskMapper.toResponseList(tasks));
    }

    /**
     * Возвращает данные задачи по идентификатору.
     *
     * @param taskId идентификатор задачи
     * @return данные задачи
     */
    @GetMapping("/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable UUID taskId) {
        log.info("Запрос на получение задачи id: {}", taskId);
        Task task = taskService.findById(taskId);
        log.debug("Задача найдена: {}", task.getTitle());
        return ResponseEntity.ok(taskMapper.toResponse(task));
    }

    /**
     * Создаёт задачу в выбранном проекте.
     *
     * @param projectId идентификатор проекта
     * @param request данные создания задачи
     * @return созданная задача
     */
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

    /**
     * Обновляет поля задачи.
     *
     * @param taskId идентификатор задачи
     * @param request данные обновления задачи
     * @return обновлённая задача
     */
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

    /**
     * Изменяет статус задачи в рабочем процессе.
     *
     * @param taskId идентификатор задачи
     * @param request данные нового статуса
     * @return задача после изменения статуса
     */
    @PatchMapping("/{taskId}/status")
    public ResponseEntity<TaskResponse> changeStatus(
            @PathVariable UUID taskId,
            @Valid @RequestBody TaskStatusUpdateRequest request) {
        log.info("Запрос на изменение статуса задачи id: {} -> {}", taskId, request.getStatus());
        Task task = taskService.changeStatus(taskId, request.getStatus());
        log.info("Статус задачи изменён: {}", task.getStatus());
        return ResponseEntity.ok(taskMapper.toResponse(task));
    }

    /**
     * Удаляет задачу по идентификатору.
     *
     * @param taskId идентификатор задачи
     * @return пустой ответ со статусом 204
     */
    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID taskId) {
        log.info("Запрос на удаление задачи id: {}", taskId);
        taskService.delete(taskId);
        log.info("Задача удалена: {}", taskId);
        return ResponseEntity.noContent().build();
    }
}
