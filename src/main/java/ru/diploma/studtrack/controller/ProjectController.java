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
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getMyProjects() {
        log.info("Запрос на получение проектов текущего пользователя");
        List<Project> projects = projectService.getMyProjects();
        log.debug("Найдено {} проектов", projects.size());
        return ResponseEntity.ok(projectMapper.toResponseList(projects));
    }

    @GetMapping("/owned")
    public ResponseEntity<List<ProjectResponse>> getOwnedProjects() {
        log.info("Запрос на получение проектов, где пользователь владелец");
        List<Project> projects = projectService.getOwnedProjects();
        log.debug("Найдено {} проектов во владении", projects.size());
        return ResponseEntity.ok(projectMapper.toResponseList(projects));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(@PathVariable UUID id) {
        log.info("Запрос на получение проекта с id: {}", id);
        Project project = projectService.findById(id);
        log.debug("Проект найден: {}", project.getName());
        return ResponseEntity.ok(projectMapper.toResponse(project));
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody ProjectCreateRequest request) {
        log.info("Запрос на создание проекта: name='{}', description='{}'", 
                request.getName(), request.getDescription());
        Project project = projectService.create(request.getName(), request.getDescription());
        log.info("Проект создан с id: {}", project.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(projectMapper.toResponse(project));
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable UUID id) {
        log.info("Запрос на удаление проекта id: {}", id);
        projectService.delete(id);
        log.info("Проект удалён: {}", id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{projectId}/members")
    public ResponseEntity<List<ProjectMemberResponse>> getMembers(@PathVariable UUID projectId) {
        log.info("Запрос на получение участников проекта id: {}", projectId);
        List<ProjectMember> members = projectService.getMembers(projectId);
        log.debug("Найдено {} участников", members.size());
        return ResponseEntity.ok(projectMemberMapper.toResponseList(members));
    }

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

    @DeleteMapping("/{projectId}/members/{userId}")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID projectId,
            @PathVariable UUID userId) {
        log.info("Запрос на удаление участника {} из проекта {}", userId, projectId);
        projectService.removeMember(projectId, userId);
        log.info("Участник удалён");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{projectId}/leave")
    public ResponseEntity<Void> leaveProject(@PathVariable UUID projectId) {
        log.info("Запрос на выход из проекта {}", projectId);
        projectService.leaveProject(projectId);
        log.info("Пользователь покинул проект {}", projectId);
        return ResponseEntity.noContent().build();
    }
}