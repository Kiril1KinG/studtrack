package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.ProjectMemberRepository;
import ru.diploma.studtrack.repository.ProjectRepository;
import ru.diploma.studtrack.repository.TaskAssigneeRepository;
import ru.diploma.studtrack.repository.TaskReviewerRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private TaskAssigneeRepository taskAssigneeRepository;
    @Mock
    private TaskReviewerRepository taskReviewerRepository;
    @Mock
    private UserService userService;
    @Mock
    private NotificationService notificationService;

    private ProjectService service;

    @BeforeEach
    void setUp() {
        service = new ProjectService(
                projectRepository,
                projectMemberRepository,
                taskAssigneeRepository,
                taskReviewerRepository,
                userService,
                notificationService
        );
    }

    @Test
    void getMyProjectsShouldUseCurrentUserId() {
        UUID userId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(projectRepository.findAllByMemberId(userId)).thenReturn(List.of());
        assertEquals(List.of(), service.getMyProjects());
    }

    @Test
    void createShouldPersistProjectAndAutoAddOwnerAsMember() {
        UUID userId = UUID.randomUUID();
        User current = User.builder().id(userId).build();
        Project saved = Project.builder().id(UUID.randomUUID()).name("P").owner(current).build();
        when(userService.getCurrentUser()).thenReturn(current);
        when(userService.findById(userId)).thenReturn(current);
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(projectRepository.save(any(Project.class))).thenReturn(saved);
        when(projectRepository.findById(saved.getId())).thenReturn(Optional.of(saved));
        when(projectMemberRepository.existsByProjectIdAndUserId(saved.getId(), userId)).thenReturn(false);
        when(projectMemberRepository.save(any(ProjectMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Project result = service.create("P", "D");

        assertEquals(saved, result);
        verify(projectMemberRepository).save(any(ProjectMember.class));
    }

    @Test
    void addMemberShouldThrowWhenAlreadyExists() {
        UUID projectId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).owner(User.builder().id(ownerId).build()).build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userService.getCurrentUserId()).thenReturn(ownerId);
        when(userService.findById(memberId)).thenReturn(User.builder().id(memberId).build());
        when(userService.getCurrentUser()).thenReturn(User.builder().id(ownerId).build());
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, memberId)).thenReturn(true);

        assertThrows(AlreadyExistsException.class, () -> service.addMember(projectId, memberId));
    }

    @Test
    void removeMemberShouldDeleteAssigneeReviewerAndMembership() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).owner(User.builder().id(ownerId).build()).build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userService.getCurrentUserId()).thenReturn(ownerId);

        service.removeMember(projectId, userId);

        verify(taskAssigneeRepository).deleteByTaskProjectIdAndUserId(projectId, userId);
        verify(taskReviewerRepository).deleteByProjectIdAndUserId(projectId, userId);
        verify(projectMemberRepository).deleteByProjectIdAndUserId(projectId, userId);
    }

    @Test
    void removeMemberShouldRejectOwnerRemoval() {
        UUID projectId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Project project = Project.builder().id(projectId).owner(User.builder().id(ownerId).build()).build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(userService.getCurrentUserId()).thenReturn(ownerId);

        assertThrows(InvalidStateException.class, () -> service.removeMember(projectId, ownerId));
    }

    @Test
    void checkMembershipShouldThrowWhenUserNotMember() {
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.checkMembership(projectId));
    }
}

