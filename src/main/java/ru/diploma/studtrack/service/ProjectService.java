package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.ProjectMemberRepository;
import ru.diploma.studtrack.repository.ProjectRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;

    public List<Project> getMyProjects() {
        UUID currentUserId = userService.getCurrentUserId();
        return projectRepository.findAllByMemberId(currentUserId);
    }

    public List<Project> getOwnedProjects() {
        UUID currentUserId = userService.getCurrentUserId();
        return projectRepository.findByOwnerId(currentUserId);
    }

    public Project findById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Проект", id));
    }

    @Transactional
    public Project create(String name, String description) {
        User currentUser = userService.getCurrentUser();
        Project project = Project.builder()
                .name(name)
                .description(description)
                .owner(currentUser)
                .build();
        Project saved = projectRepository.save(project);
        addMember(saved.getId(), currentUser.getId());
        return saved;
    }

    @Transactional
    public Project update(UUID id, String name, String description) {
        Project project = findById(id);
        checkOwnership(project);
        project.setName(name);
        project.setDescription(description);
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(UUID id) {
        Project project = findById(id);
        checkOwnership(project);
        projectRepository.delete(project);
    }

    // Управление участниками

    public List<ProjectMember> getMembers(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId);
    }

    public boolean isMember(UUID projectId, UUID userId) {
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, userId);
    }

    public boolean isOwner(UUID projectId, UUID userId) {
        return projectRepository.isOwner(projectId, userId);
    }

    @Transactional
    public ProjectMember addMember(UUID projectId, UUID userId) {
        Project project = findById(projectId);
        User user = userService.findById(userId);
        checkOwnership(project);

        if (isMember(projectId, userId)) {
            throw new AlreadyExistsException("Участник уже состоит в проекте");
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(user)
                .build();
        return projectMemberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId) {
        Project project = findById(projectId);
        checkOwnership(project);

        if (project.getOwner().getId().equals(userId)) {
            throw new InvalidStateException("Невозможно удалить владельца проекта");
        }

        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    @Transactional
    public void leaveProject(UUID projectId) {
        UUID currentUserId = userService.getCurrentUserId();
        Project project = findById(projectId);

        if (project.getOwner().getId().equals(currentUserId)) {
            throw new InvalidStateException("Владелец не может покинуть проект. Передайте права или удалите проект.");
        }

        projectMemberRepository.deleteByProjectIdAndUserId(projectId, currentUserId);
    }

    // Проверка прав

    private void checkOwnership(Project project) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!project.getOwner().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Только владелец проекта может выполнить это действие");
        }
    }

    public void checkMembership(UUID projectId) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!isMember(projectId, currentUserId)) {
            throw new AccessDeniedException("Вы не являетесь участником этого проекта");
        }
    }
}