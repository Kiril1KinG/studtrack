package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.CommentRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final ProjectService projectService;
    private final TaskReviewRoundService roundService;

    public List<Comment> getByTask(UUID taskId) {
        return commentRepository.findByTaskIdOrderByCreatedAtAsc(taskId);
    }

    public Comment findById(UUID id) {
        return commentRepository.findById(id)
                .orElseThrow(() ->new NotFoundException("Комментарий", id));
    }

    @Transactional
    public Comment addCommentToTask(UUID taskId, String content) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        User currentUser = userService.getCurrentUser();

        Comment comment = Comment.builder()
                .task(task)
                .author(currentUser)
                .content(content)
                .round(null) // Общий комментарий к задаче
                .build();

        return commentRepository.save(comment);
    }

    @Transactional
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

        return commentRepository.save(comment);
    }

    @Transactional
    public Comment updateContent(UUID id, String content) {
        Comment comment = findById(id);
        checkAuthorship(comment);
        comment.setContent(content);
        return commentRepository.save(comment);
    }

    @Transactional
    public void delete(UUID id) {
        Comment comment = findById(id);
        checkAuthorship(comment);
        commentRepository.delete(comment);
    }

    private void checkAuthorship(Comment comment) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!comment.getAuthor().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Вы можете редактировать или удалять только свои комментарии");
        }
    }
}