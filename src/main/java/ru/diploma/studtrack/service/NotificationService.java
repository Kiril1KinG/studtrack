package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.Notification;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.NotificationRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    public long getUnreadCountForCurrentUser() {
        return notificationRepository.countByRecipientIdAndReadFalse(userService.getCurrentUserId());
    }

    public List<Notification> getRecentForCurrentUser() {
        return notificationRepository.findTop15ByRecipientIdOrderByCreatedAtDesc(userService.getCurrentUserId());
    }

    public List<Notification> getAllForCurrentUser() {
        return notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(userService.getCurrentUserId());
    }

    public List<Notification> getUnreadForCurrentUser() {
        return notificationRepository.findByRecipientIdAndReadFalse(userService.getCurrentUserId()).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void markAsRead(UUID notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotFoundException("Уведомление", notificationId));
        if (!notification.getRecipient().getId().equals(userService.getCurrentUserId())) {
            throw new AccessDeniedException("Нельзя изменять чужие уведомления");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllAsReadForCurrentUser() {
        List<Notification> notifications = notificationRepository.findByRecipientIdAndReadFalse(userService.getCurrentUserId());
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void notifyProjectInvitation(User recipient, User actor, UUID projectId, String projectName) {
        create(recipient, actor, Notification.NotificationType.PROJECT_INVITATION,
                actor.getFullName() + " добавил(а) вас в проект \"" + projectName + "\"",
                "/projects/" + projectId);
    }

    @Transactional
    public void notifyTaskAssigned(User recipient, User actor, Task task) {
        create(recipient, actor, Notification.NotificationType.TASK_ASSIGNED,
                actor.getFullName() + " назначил(а) вас исполнителем задачи \"" + task.getTitle() + "\"",
                "/tasks/" + task.getId());
    }

    @Transactional
    public void notifyReviewerAssigned(User recipient, User actor, Task task) {
        create(recipient, actor, Notification.NotificationType.REVIEWER_ASSIGNED,
                actor.getFullName() + " назначил(а) вас ревьюером задачи \"" + task.getTitle() + "\"",
                "/tasks/" + task.getId());
    }

    @Transactional
    public void notifyRoundRecheck(Task task, User actor, List<TaskReviewer> reviewersToRecheck) {
        for (TaskReviewer reviewer : reviewersToRecheck) {
            create(reviewer.getUser(), actor, Notification.NotificationType.REVIEW_RECHECK,
                    "По задаче \"" + task.getTitle() + "\" нужен повторный review",
                    "/tasks/" + task.getId());
        }
    }

    @Transactional
    public void notifyChangeRequestCreated(ChangeRequest changeRequest, User actor) {
        Task task = changeRequest.getTask();
        Set<User> recipients = new LinkedHashSet<>();
        task.getAssignees().forEach(a -> recipients.add(a.getUser()));
        recipients.add(task.getProject().getOwner());
        recipients.remove(actor);

        for (User recipient : recipients) {
            create(recipient, actor, Notification.NotificationType.CHANGE_REQUEST_CREATED,
                    "Новое замечание в задаче \"" + task.getTitle() + "\"",
                    "/tasks/" + task.getId());
        }
    }

    @Transactional
    public void notifyCommentAdded(Comment comment, User actor) {
        Task task = comment.getTask();
        Set<User> recipients = new LinkedHashSet<>();
        task.getAssignees().forEach(a -> recipients.add(a.getUser()));
        task.getReviewers().forEach(r -> recipients.add(r.getUser()));
        recipients.add(task.getProject().getOwner());
        if (comment.getChangeRequest() != null) {
            recipients.add(comment.getChangeRequest().getAuthor());
        }
        recipients.remove(actor);

        for (User recipient : recipients) {
            create(recipient, actor, Notification.NotificationType.COMMENT_ADDED,
                    actor.getFullName() + " добавил(а) комментарий в задаче \"" + task.getTitle() + "\"",
                    "/tasks/" + task.getId());
        }
    }

    private void create(User recipient,
                        User actor,
                        Notification.NotificationType type,
                        String message,
                        String targetUrl) {
        if (recipient == null || actor == null || recipient.getId().equals(actor.getId())) {
            return;
        }
        Notification notification = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .message(message)
                .targetUrl(targetUrl)
                .read(false)
                .build();
        notificationRepository.save(notification);
    }
}
