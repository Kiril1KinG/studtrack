package ru.diploma.studtrack.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.ChangeRequestRepository;
import ru.diploma.studtrack.repository.CommentRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private ChangeRequestRepository changeRequestRepository;
    @Mock private TaskService taskService;
    @Mock private UserService userService;
    @Mock private ProjectService projectService;
    @Mock private TaskReviewRoundService roundService;
    @Mock private NotificationService notificationService;
    @Mock private TaskAttachmentService taskAttachmentService;
    @Mock private EntityManager entityManager;

    @InjectMocks
    private CommentService service;

    private UUID taskId;
    private Task task;
    private User author;

    @BeforeEach
    void setUp() throws Exception {
        taskId = UUID.randomUUID();
        task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        author = User.builder().id(UUID.randomUUID()).build();

        java.lang.reflect.Field emField = CommentService.class.getDeclaredField("entityManager");
        emField.setAccessible(true);
        emField.set(service, entityManager);
    }

    @Test
    void addCommentToTaskShouldAttachNotifyAndReloadDetailed() {
        Comment saved = Comment.builder().id(UUID.randomUUID()).task(task).author(author).content("c").build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(author);
        when(commentRepository.save(any(Comment.class))).thenReturn(saved);
        when(commentRepository.findDetailedById(saved.getId())).thenReturn(Optional.of(saved));

        Comment result = service.addCommentToTask(taskId, "c", List.of());

        assertEquals(saved, result);
        verify(taskAttachmentService).attachToComment(taskId, saved, List.of());
        verify(notificationService).notifyCommentAdded(saved, author);
        verify(entityManager).flush();
        verify(entityManager).clear();
    }

    @Test
    void addCommentToChangeRequestShouldResolveTaskAndSave() {
        UUID crId = UUID.randomUUID();
        TaskReviewRound round = TaskReviewRound.builder().task(task).build();
        ChangeRequest cr = ChangeRequest.builder().id(crId).round(round).build();
        Comment saved = Comment.builder().id(UUID.randomUUID()).task(task).author(author).build();
        when(changeRequestRepository.findById(crId)).thenReturn(Optional.of(cr));
        when(userService.getCurrentUser()).thenReturn(author);
        when(commentRepository.save(any(Comment.class))).thenReturn(saved);
        when(commentRepository.findDetailedById(saved.getId())).thenReturn(Optional.of(saved));

        Comment result = service.addCommentToChangeRequest(crId, "text", List.of());
        assertEquals(saved, result);
    }

    @Test
    void updateContentShouldCheckAuthorAndManageAttachments() {
        UUID commentId = UUID.randomUUID();
        Comment comment = Comment.builder().id(commentId).task(task).author(author).content("old").build();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userService.getCurrentUserId()).thenReturn(author.getId());
        when(commentRepository.save(comment)).thenReturn(comment);
        when(commentRepository.findDetailedById(commentId)).thenReturn(Optional.of(comment));

        Comment updated = service.updateContent(commentId, "new", List.of(), List.of());
        assertEquals("new", updated.getContent());
        verify(taskAttachmentService).attachToComment(taskId, comment, List.of());
        verify(taskAttachmentService).deleteCommentAttachments(comment, List.of());
    }

    @Test
    void updateContentShouldRejectForeignAuthor() {
        UUID commentId = UUID.randomUUID();
        Comment comment = Comment.builder().id(commentId).author(author).task(task).build();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class, () -> service.updateContent(commentId, "x"));
    }

    @Test
    void deleteShouldCheckAuthorship() {
        UUID commentId = UUID.randomUUID();
        Comment comment = Comment.builder().id(commentId).author(author).task(task).build();
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(userService.getCurrentUserId()).thenReturn(author.getId());

        service.delete(commentId);
        verify(commentRepository).delete(comment);
    }
}

