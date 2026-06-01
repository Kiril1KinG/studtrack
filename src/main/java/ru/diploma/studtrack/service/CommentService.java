package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.ChangeRequestRepository;
import ru.diploma.studtrack.repository.CommentRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * Управляет комментариями задач, раундов ревью и замечаний.
 */
public class CommentService {

    /**
     * Репозиторий комментариев.
     */
    private final CommentRepository commentRepository;
    /**
     * Репозиторий замечаний.
     */
    private final ChangeRequestRepository changeRequestRepository;
    /**
     * Сервис задач.
     */
    private final TaskService taskService;
    /**
     * Сервис пользователей.
     */
    private final UserService userService;
    /**
     * Сервис проверки доступа к проекту.
     */
    private final ProjectService projectService;
    /**
     * Сервис раундов ревью.
     */
    private final TaskReviewRoundService roundService;
    /**
     * Сервис уведомлений.
     */
    private final NotificationService notificationService;
    /**
     * Сервис вложений комментария.
     */
    private final TaskAttachmentService taskAttachmentService;
    /**
     * EntityManager для принудительной синхронизации и повторной загрузки сущностей.
     */
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Возвращает комментарии задачи в порядке от новых к старым.
     *
     * @param taskId идентификатор задачи
     * @return список комментариев
     */
    public List<Comment> getByTask(UUID taskId) {
        return commentRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    /**
     * Возвращает комментарии замечания в порядке от новых к старым.
     *
     * @param changeRequestId идентификатор замечания
     * @return список комментариев
     */
    public List<Comment> getByChangeRequest(UUID changeRequestId) {
        return commentRepository.findByChangeRequestIdOrderByCreatedAtDesc(changeRequestId);
    }

    /**
     * Возвращает комментарий по идентификатору.
     *
     * @param id идентификатор комментария
     * @return комментарий
     */
    public Comment findById(UUID id) {
        return commentRepository.findById(id)
                .orElseThrow(() ->new NotFoundException("Комментарий", id));
    }

    @Transactional
    /**
     * Добавляет комментарий к задаче без вложений.
     *
     * @param taskId идентификатор задачи
     * @param content текст комментария
     * @return созданный комментарий
     */
    public Comment addCommentToTask(UUID taskId, String content) {
        return addCommentToTask(taskId, content, List.of());
    }

    @Transactional
    /**
     * Добавляет комментарий к задаче с вложениями.
     *
     * @param taskId идентификатор задачи
     * @param content текст комментария
     * @param attachmentIds идентификаторы вложений
     * @return созданный комментарий
     */
    public Comment addCommentToTask(UUID taskId, String content, List<UUID> attachmentIds) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        User currentUser = userService.getCurrentUser();

        Comment comment = Comment.builder()
                .task(task)
                .author(currentUser)
                .content(content)
                .round(null) // Общий комментарий к задаче
                .build();

        Comment saved = commentRepository.save(comment);
        taskAttachmentService.attachToComment(taskId, saved, attachmentIds);
        notificationService.notifyCommentAdded(saved, currentUser);
        entityManager.flush();
        entityManager.clear();
        return commentRepository.findDetailedById(saved.getId())
                .orElse(saved);
    }

    @Transactional
    /**
     * Добавляет комментарий к раунду ревью.
     *
     * @param taskId идентификатор задачи
     * @param roundId идентификатор раунда
     * @param content текст комментария
     * @return созданный комментарий
     */
    public Comment addCommentToRound(UUID taskId, UUID roundId, String content) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        TaskReviewRound round = roundService.findById(roundId);
        User currentUser = userService.getCurrentUser();

        Comment comment = Comment.builder()
                .task(task)
                .author(currentUser)
                .content(content)
                .round(round)
                .build();

        Comment saved = commentRepository.save(comment);
        notificationService.notifyCommentAdded(saved, currentUser);
        return saved;
    }

    @Transactional
    /**
     * Добавляет комментарий к замечанию без вложений.
     *
     * @param changeRequestId идентификатор замечания
     * @param content текст комментария
     * @return созданный комментарий
     */
    public Comment addCommentToChangeRequest(UUID changeRequestId, String content) {
        return addCommentToChangeRequest(changeRequestId, content, List.of());
    }

    @Transactional
    /**
     * Добавляет комментарий к замечанию с вложениями.
     *
     * @param changeRequestId идентификатор замечания
     * @param content текст комментария
     * @param attachmentIds идентификаторы вложений
     * @return созданный комментарий
     */
    public Comment addCommentToChangeRequest(UUID changeRequestId, String content, List<UUID> attachmentIds) {
        ChangeRequest cr = changeRequestRepository.findById(changeRequestId)
                .orElseThrow(() -> new NotFoundException("Замечание", changeRequestId));
        Task task = cr.getRound().getTask();
        projectService.checkMembership(task.getProject().getId());

        User currentUser = userService.getCurrentUser();

        Comment comment = Comment.builder()
                .task(task)
                .author(currentUser)
                .content(content)
                .round(cr.getRound())
                .changeRequest(cr)
                .build();

        Comment saved = commentRepository.save(comment);
        taskAttachmentService.attachToComment(task.getId(), saved, attachmentIds);
        notificationService.notifyCommentAdded(saved, currentUser);
        entityManager.flush();
        entityManager.clear();
        return commentRepository.findDetailedById(saved.getId())
                .orElse(saved);
    }

    @Transactional
    /**
     * Обновляет текст комментария без изменения вложений.
     *
     * @param id идентификатор комментария
     * @param content новый текст
     * @return обновлённый комментарий
     */
    public Comment updateContent(UUID id, String content) {
        return updateContent(id, content, List.of(), List.of());
    }

    @Transactional
    /**
     * Обновляет текст комментария и изменяет его вложения.
     *
     * @param id идентификатор комментария
     * @param content новый текст
     * @param attachmentIds идентификаторы добавляемых вложений
     * @param removedAttachmentIds идентификаторы удаляемых вложений
     * @return обновлённый комментарий
     */
    public Comment updateContent(UUID id, String content, List<UUID> attachmentIds, List<UUID> removedAttachmentIds) {
        Comment comment = findById(id);
        checkAuthorship(comment);
        comment.setContent(content);
        taskAttachmentService.attachToComment(
                comment.getTask().getId(),
                comment,
                attachmentIds != null ? attachmentIds : List.of()
        );
        taskAttachmentService.deleteCommentAttachments(
                comment,
                removedAttachmentIds != null ? removedAttachmentIds : List.of()
        );
        Comment saved = commentRepository.save(comment);
        entityManager.flush();
        entityManager.clear();
        return commentRepository.findDetailedById(saved.getId()).orElse(saved);
    }

    @Transactional
    /**
     * Удаляет комментарий.
     *
     * @param id идентификатор комментария
     */
    public void delete(UUID id) {
        Comment comment = findById(id);
        checkAuthorship(comment);
        commentRepository.delete(comment);
    }

    /**
     * Проверяет, что текущий пользователь является автором комментария.
     *
     * @param comment комментарий
     */
    private void checkAuthorship(Comment comment) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!comment.getAuthor().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Вы можете редактировать или удалять только свои комментарии");
        }
    }
}