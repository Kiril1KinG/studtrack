package ru.diploma.studtrack.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diploma.studtrack.dto.request.CommentCreateRequest;
import ru.diploma.studtrack.dto.request.CommentUpdateRequest;
import ru.diploma.studtrack.dto.response.CommentResponse;
import ru.diploma.studtrack.mapper.CommentMapper;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.service.CommentService;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;
    private final CommentMapper commentMapper;

    @GetMapping("/tasks/{taskId}/comments")
    public ResponseEntity<List<CommentResponse>> getTaskComments(@PathVariable UUID taskId) {
        log.info("Запрос на получение комментариев задачи id: {}", taskId);
        List<Comment> comments = commentService.getByTask(taskId);
        log.debug("Найдено {} комментариев", comments.size());
        return ResponseEntity.ok(commentMapper.toResponseList(comments));
    }

    @PostMapping("/tasks/{taskId}/comments")
    public ResponseEntity<CommentResponse> addTaskComment(
            @PathVariable UUID taskId,
            @Valid @RequestBody CommentCreateRequest request) {
        log.info("Запрос на добавление комментария к задаче {}: content='{}'", 
                taskId, request.getContent());
        Comment comment = commentService.addCommentToTask(taskId, request.getContent());
        log.info("Комментарий создан с id: {}", comment.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(commentMapper.toResponse(comment));
    }

    @PostMapping("/tasks/{taskId}/rounds/{roundId}/comments")
    public ResponseEntity<CommentResponse> addRoundComment(
            @PathVariable UUID taskId,
            @PathVariable UUID roundId,
            @Valid @RequestBody CommentCreateRequest request) {
        log.info("Запрос на добавление комментария к задаче {}, итерации {}: content='{}'", 
                taskId, roundId, request.getContent());
        Comment comment = commentService.addCommentToRound(taskId, roundId, request.getContent());
        log.info("Комментарий создан с id: {}", comment.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(commentMapper.toResponse(comment));
    }

    @PutMapping("/comments/{id}")
    public ResponseEntity<CommentResponse> updateComment(
            @PathVariable UUID id,
            @Valid @RequestBody CommentUpdateRequest request) {
        log.info("Запрос на обновление комментария id: {}", id);
        Comment comment = commentService.updateContent(id, request.getContent());
        log.info("Комментарий обновлён");
        return ResponseEntity.ok(commentMapper.toResponse(comment));
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable UUID id) {
        log.info("Запрос на удаление комментария id: {}", id);
        commentService.delete(id);
        log.info("Комментарий удалён");
        return ResponseEntity.noContent().build();
    }
}