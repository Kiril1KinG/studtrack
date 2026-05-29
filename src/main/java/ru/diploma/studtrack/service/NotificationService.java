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
/**
 * Управляет пользовательскими уведомлениями и их доставкой.
 */
public class NotificationService {

    /**
     * Репозиторий уведомлений.
     */
    private final NotificationRepository notificationRepository;
    /**
     * Сервис текущего пользователя.
     */
    private final UserService userService;

    /**
     * Возвращает количество непрочитанных уведомлений текущего пользователя.
     *
     * @return количество непрочитанных уведомлений
     */
    public long getUnreadCountForCurrentUser() {
        return notificationRepository.countByRecipientIdAndReadFalse(userService.getCurrentUserId());
    }

    /**
     * Возвращает последние уведомления текущего пользователя.
     *
     * @return список последних уведомлений
     */
    public List<Notification> getRecentForCurrentUser() {
        return notificationRepository.findTop15ByRecipientIdOrderByCreatedAtDesc(userService.getCurrentUserId());
    }

    /**
     * Возвращает список уведомлений текущего пользователя.
     *
     * @return список уведомлений
     */
    public List<Notification> getAllForCurrentUser() {
        return notificationRepository.findTop50ByRecipientIdOrderByCreatedAtDesc(userService.getCurrentUserId());
    }

    /**
     * Возвращает непрочитанные уведомления текущего пользователя.
     *
     * @return список непрочитанных уведомлений
     */
    public List<Notification> getUnreadForCurrentUser() {
        return notificationRepository.findByRecipientIdAndReadFalse(userService.getCurrentUserId()).stream()
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    @Transactional
    /**
     * Помечает уведомление как прочитанное.
     *
     * @param notificationId идентификатор уведомления
     */
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
    /**
     * Помечает все уведомления текущего пользователя как прочитанные.
     */
    public void markAllAsReadForCurrentUser() {
        List<Notification> notifications = notificationRepository.findByRecipientIdAndReadFalse(userService.getCurrentUserId());
        notifications.forEach(n -> n.setRead(true));
        notificationRepository.saveAll(notifications);
    }

    @Transactional
    /**
     * Создаёт уведомление о приглашении в проект.
     *
     * @param recipient получатель
     * @param actor инициатор действия
     * @param projectId идентификатор проекта
     * @param projectName название проекта
     */
    public void notifyProjectInvitation(User recipient, User actor, UUID projectId, String projectName) {
        create(recipient, actor, Notification.NotificationType.PROJECT_INVITATION,
                actor.getFullName() + " добавил(а) вас в проект \"" + projectName + "\"",
                "/projects/" + projectId);
    }

    @Transactional
    /**
     * Создаёт уведомление о назначении исполнителем.
     *
     * @param recipient получатель
     * @param actor инициатор действия
     * @param task задача
     */
    public void notifyTaskAssigned(User recipient, User actor, Task task) {
        create(recipient, actor, Notification.NotificationType.TASK_ASSIGNED,
                actor.getFullName() + " назначил(а) вас исполнителем задачи \"" + task.getTitle() + "\"",
                "/tasks/" + task.getId());
    }

    @Transactional
    /**
     * Создаёт уведомление о назначении ревьюером.
     *
     * @param recipient получатель
     * @param actor инициатор действия
     * @param task задача
     */
    public void notifyReviewerAssigned(User recipient, User actor, Task task) {
        create(recipient, actor, Notification.NotificationType.REVIEWER_ASSIGNED,
                actor.getFullName() + " назначил(а) вас ревьюером задачи \"" + task.getTitle() + "\"",
                "/tasks/" + task.getId());
    }

    @Transactional
    /**
     * Создаёт уведомления о необходимости повторной проверки задачи.
     *
     * @param task задача
     * @param actor инициатор действия
     * @param reviewersToRecheck ревьюеры для повторной проверки
     */
    public void notifyRoundRecheck(Task task, User actor, List<TaskReviewer> reviewersToRecheck) {
        for (TaskReviewer reviewer : reviewersToRecheck) {
            create(reviewer.getUser(), actor, Notification.NotificationType.REVIEW_RECHECK,
                    "По задаче \"" + task.getTitle() + "\" нужен повторный review",
                    "/tasks/" + task.getId());
        }
    }

    @Transactional
    /**
     * Создаёт уведомления о новом замечании.
     *
     * @param changeRequest созданное замечание
     * @param actor инициатор действия
     */
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
    /**
     * Создаёт уведомления о добавлении комментария.
     *
     * @param comment добавленный комментарий
     * @param actor инициатор действия
     */
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

    /**
     * Создаёт и сохраняет уведомление, если получатель и инициатор различаются.
     *
     * @param recipient получатель
     * @param actor инициатор действия
     * @param type тип уведомления
     * @param message текст уведомления
     * @param targetUrl целевой URL
     */
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
