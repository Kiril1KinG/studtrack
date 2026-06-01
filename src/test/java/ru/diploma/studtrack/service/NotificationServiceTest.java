package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.Notification;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.NotificationRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserService userService;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, userService);
    }

    @Test
    void getUnreadCountForCurrentUserShouldDelegateToRepository() {
        UUID userId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(notificationRepository.countByRecipientIdAndReadFalse(userId)).thenReturn(3L);

        assertEquals(3L, service.getUnreadCountForCurrentUser());
    }

    @Test
    void getUnreadForCurrentUserShouldSortByCreatedAtDesc() {
        UUID userId = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(userId);
        Notification oldItem = Notification.builder().createdAt(LocalDateTime.now().minusHours(2)).build();
        Notification newItem = Notification.builder().createdAt(LocalDateTime.now()).build();
        when(notificationRepository.findByRecipientIdAndReadFalse(userId)).thenReturn(List.of(oldItem, newItem));

        List<Notification> result = service.getUnreadForCurrentUser();

        assertEquals(List.of(newItem, oldItem), result);
    }

    @Test
    void markAsReadShouldThrowWhenNotificationNotFound() {
        UUID id = UUID.randomUUID();
        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.markAsRead(id));
    }

    @Test
    void markAsReadShouldThrowForForeignNotification() {
        UUID current = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(current);
        Notification notification = Notification.builder()
                .id(id)
                .recipient(User.builder().id(other).build())
                .build();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        assertThrows(AccessDeniedException.class, () -> service.markAsRead(id));
    }

    @Test
    void markAsReadShouldPersistStateForOwner() {
        UUID current = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(current);
        Notification notification = Notification.builder()
                .id(id)
                .recipient(User.builder().id(current).build())
                .read(false)
                .build();
        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        service.markAsRead(id);

        assertEquals(true, notification.isRead());
        verify(notificationRepository).save(notification);
    }

    @Test
    void markAllAsReadForCurrentUserShouldPersistAll() {
        UUID current = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(current);
        Notification n1 = Notification.builder().read(false).build();
        Notification n2 = Notification.builder().read(false).build();
        when(notificationRepository.findByRecipientIdAndReadFalse(current)).thenReturn(List.of(n1, n2));

        service.markAllAsReadForCurrentUser();

        assertEquals(true, n1.isRead());
        assertEquals(true, n2.isRead());
        verify(notificationRepository).saveAll(List.of(n1, n2));
    }

    @Test
    void notifyProjectInvitationShouldSaveNotification() {
        User recipient = User.builder().id(UUID.randomUUID()).build();
        User actor = User.builder().id(UUID.randomUUID()).firstName("Иван").lastName("Иванов").build();

        service.notifyProjectInvitation(recipient, actor, UUID.randomUUID(), "Proj");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void notifyCommentAddedShouldNotNotifyActor() {
        User actor = User.builder().id(UUID.randomUUID()).firstName("Иван").lastName("Иванов").build();
        User assignee = User.builder().id(UUID.randomUUID()).build();
        User reviewer = User.builder().id(UUID.randomUUID()).build();
        User owner = User.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder()
                .id(UUID.randomUUID())
                .title("Task")
                .project(Project.builder().owner(owner).build())
                .assignees(Set.of(TaskAssignee.builder().user(assignee).build()))
                .reviewers(Set.of(TaskReviewer.builder().user(reviewer).build()))
                .build();
        Comment comment = Comment.builder().task(task).build();

        service.notifyCommentAdded(comment, actor);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        List<UUID> recipients = captor.getAllValues().stream().map(n -> n.getRecipient().getId()).toList();
        assertEquals(false, recipients.contains(actor.getId()));
    }

    @Test
    void notifyTaskAssignedShouldSkipWhenRecipientIsActor() {
        User actor = User.builder().id(UUID.randomUUID()).firstName("Иван").lastName("Иванов").build();
        Task task = Task.builder().id(UUID.randomUUID()).title("Task").build();

        service.notifyTaskAssigned(actor, actor, task);

        verify(notificationRepository, never()).save(any(Notification.class));
    }
}

