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
import ru.diploma.studtrack.repository.TaskAssigneeRepository;
import ru.diploma.studtrack.repository.ProjectMemberRepository;
import ru.diploma.studtrack.repository.ProjectRepository;
import ru.diploma.studtrack.repository.TaskReviewerRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * Управляет проектами, участниками и проверками прав доступа.
 */
public class ProjectService {

    /**
     * Репозиторий проектов.
     */
    private final ProjectRepository projectRepository;
    /**
     * Репозиторий участников проекта.
     */
    private final ProjectMemberRepository projectMemberRepository;
    /**
     * Репозиторий связей исполнителей задач.
     */
    private final TaskAssigneeRepository taskAssigneeRepository;
    /**
     * Репозиторий связей ревьюеров задач.
     */
    private final TaskReviewerRepository taskReviewerRepository;
    /**
     * Сервис пользователей.
     */
    private final UserService userService;
    /**
     * Сервис уведомлений.
     */
    private final NotificationService notificationService;

    /**
     * Возвращает проекты текущего пользователя.
     *
     * @return список проектов
     */
    public List<Project> getMyProjects() {
        UUID currentUserId = userService.getCurrentUserId();
        return projectRepository.findAllByMemberId(currentUserId);
    }

    /**
     * Возвращает проекты, где текущий пользователь является владельцем.
     *
     * @return список проектов владельца
     */
    public List<Project> getOwnedProjects() {
        UUID currentUserId = userService.getCurrentUserId();
        return projectRepository.findByOwnerId(currentUserId);
    }

    /**
     * Возвращает проект по идентификатору.
     *
     * @param id идентификатор проекта
     * @return найденный проект
     */
    public Project findById(UUID id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Проект", id));
    }

    @Transactional
    /**
     * Создаёт проект и автоматически добавляет владельца в участники.
     *
     * @param name название проекта
     * @param description описание проекта
     * @return созданный проект
     */
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
    /**
     * Обновляет проект.
     *
     * @param id идентификатор проекта
     * @param name новое название
     * @param description новое описание
     * @return обновлённый проект
     */
    public Project update(UUID id, String name, String description) {
        Project project = findById(id);
        checkOwnership(project);
        project.setName(name);
        project.setDescription(description);
        return projectRepository.save(project);
    }

    @Transactional
    /**
     * Удаляет проект.
     *
     * @param id идентификатор проекта
     */
    public void delete(UUID id) {
        Project project = findById(id);
        checkOwnership(project);
        projectRepository.delete(project);
    }

    /**
     * Возвращает участников проекта.
     *
     * @param projectId идентификатор проекта
     * @return список участников
     */
    public List<ProjectMember> getMembers(UUID projectId) {
        return projectMemberRepository.findByProjectId(projectId);
    }

    /**
     * Проверяет, состоит ли пользователь в проекте.
     *
     * @param projectId идентификатор проекта
     * @param userId идентификатор пользователя
     * @return true, если пользователь состоит в проекте
     */
    public boolean isMember(UUID projectId, UUID userId) {
        return projectMemberRepository.existsByProjectIdAndUserId(projectId, userId);
    }

    /**
     * Проверяет, является ли пользователь владельцем проекта.
     *
     * @param projectId идентификатор проекта
     * @param userId идентификатор пользователя
     * @return true, если пользователь владелец проекта
     */
    public boolean isOwner(UUID projectId, UUID userId) {
        return projectRepository.isOwner(projectId, userId);
    }

    @Transactional
    /**
     * Добавляет участника в проект.
     *
     * @param projectId идентификатор проекта
     * @param userId идентификатор пользователя
     * @return созданная запись участника
     */
    public ProjectMember addMember(UUID projectId, UUID userId) {
        Project project = findById(projectId);
        User user = userService.findById(userId);
        User currentUser = userService.getCurrentUser();
        checkOwnership(project);

        if (isMember(projectId, userId)) {
            throw new AlreadyExistsException("Участник уже состоит в проекте");
        }

        ProjectMember member = ProjectMember.builder()
                .project(project)
                .user(user)
                .build();
        ProjectMember savedMember = projectMemberRepository.save(member);
        notificationService.notifyProjectInvitation(user, currentUser, project.getId(), project.getName());
        return savedMember;
    }

    @Transactional
    /**
     * Удаляет участника из проекта и связанные назначения по задачам.
     *
     * @param projectId идентификатор проекта
     * @param userId идентификатор пользователя
     */
    public void removeMember(UUID projectId, UUID userId) {
        Project project = findById(projectId);
        checkOwnership(project);

        if (project.getOwner().getId().equals(userId)) {
            throw new InvalidStateException("Невозможно удалить владельца проекта");
        }

        taskAssigneeRepository.deleteByTaskProjectIdAndUserId(projectId, userId);
        taskReviewerRepository.deleteByProjectIdAndUserId(projectId, userId);
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, userId);
    }

    @Transactional
    /**
     * Удаляет текущего пользователя из проекта.
     *
     * @param projectId идентификатор проекта
     */
    public void leaveProject(UUID projectId) {
        UUID currentUserId = userService.getCurrentUserId();
        Project project = findById(projectId);

        if (project.getOwner().getId().equals(currentUserId)) {
            throw new InvalidStateException("Владелец не может покинуть проект. Передайте права или удалите проект.");
        }

        taskAssigneeRepository.deleteByTaskProjectIdAndUserId(projectId, currentUserId);
        taskReviewerRepository.deleteByProjectIdAndUserId(projectId, currentUserId);
        projectMemberRepository.deleteByProjectIdAndUserId(projectId, currentUserId);
    }

    /**
     * Проверяет, что текущий пользователь является владельцем проекта.
     *
     * @param project проверяемый проект
     */
    private void checkOwnership(Project project) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!project.getOwner().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Только владелец проекта может выполнить это действие");
        }
    }

    /**
     * Проверяет членство текущего пользователя в проекте.
     *
     * @param projectId идентификатор проекта
     */
    public void checkMembership(UUID projectId) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!isMember(projectId, currentUserId)) {
            throw new AccessDeniedException("Вы не являетесь участником этого проекта");
        }
    }
}