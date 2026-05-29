package ru.diploma.studtrack.controller.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diploma.studtrack.dto.request.AddMemberRequest;
import ru.diploma.studtrack.dto.request.ProjectCreateRequest;
import ru.diploma.studtrack.dto.request.ProjectUpdateRequest;
import ru.diploma.studtrack.dto.response.ProjectMemberResponse;
import ru.diploma.studtrack.dto.response.ProjectResponse;
import ru.diploma.studtrack.mapper.ProjectMapper;
import ru.diploma.studtrack.mapper.ProjectMemberMapper;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.service.ProjectService;

import java.util.List;
import java.util.UUID;

@Slf4j
/**
 * Предоставляет REST-эндпоинты для управления проектами и их участниками.
 */
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectApiController {

    /**
     * Выполняет бизнес-логику проектов.
     */
    private final ProjectService projectService;
    /**
     * Преобразует сущности проектов в DTO-ответы API.
     */
    private final ProjectMapper projectMapper;
    /**
     * Преобразует сущности участников проекта в DTO-ответы API.
     */
    private final ProjectMemberMapper projectMemberMapper;

    /**
     * Возвращает все проекты, доступные текущему пользователю.
     *
     * @return список проектов
     */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getMyProjects() {
        log.info("Запрос на получение проектов текущего пользователя");
        List<Project> projects = projectService.getMyProjects();
        log.debug("Найдено {} проектов", projects.size());
        return ResponseEntity.ok(projectMapper.toResponseList(projects));
    }

    /**
     * Возвращает проекты, владельцем которых является текущий пользователь.
     *
     * @return список проектов владельца
     */
    @GetMapping("/owned")
    public ResponseEntity<List<ProjectResponse>> getOwnedProjects() {
        log.info("Запрос на получение проектов, где пользователь владелец");
        List<Project> projects = projectService.getOwnedProjects();
        log.debug("Найдено {} проектов во владении", projects.size());
        return ResponseEntity.ok(projectMapper.toResponseList(projects));
    }

    /**
     * Возвращает данные проекта по идентификатору.
     *
     * @param id идентификатор проекта
     * @return данные проекта
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
        log.info("Запрос на получение проекта с id: {}", id);
        Project project = projectService.findById(id);
        log.debug("Проект найден: {}", project.getName());
        return ResponseEntity.ok(projectMapper.toResponse(project));
    }

    /**
     * Создаёт новый проект.
     *
     * @param request данные создания проекта
     * @return созданный проект
     */
    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        log.info("Запрос на создание проекта: name='{}', description='{}'",
                request.getName(), request.getDescription());
        Project project = projectService.create(request.getName(), request.getDescription());
        log.info("Проект создан с id: {}", project.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(projectMapper.toResponse(project));
    }

    /**
     * Обновляет название и описание проекта.
     *
     * @param id идентификатор проекта
     * @param request данные обновления проекта
     * @return обновлённый проект
     */
    @PutMapping("/{id}")
    public ResponseEntity<ProjectResponse> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectUpdateRequest request) {
        log.info("Запрос на обновление проекта id: {}, name='{}', description='{}'",
                id, request.getName(), request.getDescription());
        Project project = projectService.update(id, request.getName(), request.getDescription());
        log.info("Проект обновлён: {}", project.getId());
        return ResponseEntity.ok(projectMapper.toResponse(project));
    }

    /**
     * Удаляет проект по идентификатору.
     *
     * @param id идентификатор проекта
     * @return пустой ответ со статусом 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        log.info("Запрос на удаление проекта id: {}", id);
        projectService.delete(id);
        log.info("Проект удалён: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Возвращает всех участников проекта.
     *
     * @param projectId идентификатор проекта
     * @return список участников
     */
    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<ProjectMemberResponse>> getMembers(@PathVariable UUID projectId) {
        log.info("Запрос на получение участников проекта id: {}", projectId);
        List<ProjectMember> members = projectService.getMembers(projectId);
        log.debug("Найдено {} участников", members.size());
        return ResponseEntity.ok(projectMemberMapper.toResponseList(members));
    }

    /**
     * Добавляет участника в проект.
     *
     * @param projectId идентификатор проекта
     * @param request данные участника
     * @return созданная запись участника проекта
     */
    @PostMapping("/{projectId}/members")
    public ResponseEntity<ProjectMemberResponse> addMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddMemberRequest request) {
        log.info("Запрос на добавление участника в проект {}: userId={}, email={}",
                projectId, request.getUserId(), request.getEmail());
        ProjectMember member = projectService.addMember(projectId, request.getUserId());
        log.info("Участник добавлен: {}", member.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(projectMemberMapper.toResponse(member));
    }

    /**
     * Удаляет участника из проекта.
     *
     * @param projectId идентификатор проекта
     * @param userId идентификатор пользователя
     * @return пустой ответ со статусом 204
     */
    @DeleteMapping("/{projectId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID userId) {
        log.info("Запрос на удаление участника {} из проекта {}", userId, projectId);
        projectService.removeMember(projectId, userId);
        log.info("Участник удалён");
        return ResponseEntity.noContent().build();
    }

    /**
     * Удаляет текущего пользователя из участников проекта.
     *
     * @param projectId идентификатор проекта
     * @return пустой ответ со статусом 204
     */
    @PostMapping("/{projectId}/leave")
    public ResponseEntity<Void> leaveProject(@PathVariable UUID projectId) {
        log.info("Запрос на выход из проекта {}", projectId);
        projectService.leaveProject(projectId);
        log.info("Пользователь покинул проект {}", projectId);
        return ResponseEntity.noContent().build();
    }
}
