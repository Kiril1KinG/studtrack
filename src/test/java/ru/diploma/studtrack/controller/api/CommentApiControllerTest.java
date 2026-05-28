package ru.diploma.studtrack.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.diploma.studtrack.dto.request.CommentCreateRequest;
import ru.diploma.studtrack.dto.request.CommentUpdateRequest;
import ru.diploma.studtrack.dto.response.CommentResponse;
import ru.diploma.studtrack.mapper.CommentMapper;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.service.CommentService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentApiControllerTest {

    @Mock
    private CommentService commentService;
    @Mock
    private CommentMapper commentMapper;

    private CommentApiController controller;

    @BeforeEach
    void setUp() {
        controller = new CommentApiController(commentService, commentMapper);
    }

    @Test
    void getTaskCommentsShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        when(commentService.getByTask(taskId)).thenReturn(List.of());
        when(commentMapper.toResponseList(List.of())).thenReturn(List.of());

        var response = controller.getTaskComments(taskId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void addTaskCommentShouldReturnCreated() {
        UUID taskId = UUID.randomUUID();
        Comment comment = Comment.builder().id(UUID.randomUUID()).build();
        CommentResponse dto = CommentResponse.builder().id(comment.getId()).content("c").build();
        when(commentService.addCommentToTask(taskId, "c", List.of())).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(dto);

        var response = controller.addTaskComment(taskId, CommentCreateRequest.builder().content("c").attachmentIds(List.of()).build());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("c", response.getBody().getContent());
    }

    @Test
    void updateAndDeleteShouldDelegate() {
        UUID commentId = UUID.randomUUID();
        Comment comment = Comment.builder().id(commentId).build();
        CommentResponse dto = CommentResponse.builder().id(commentId).build();
        when(commentService.updateContent(commentId, "u", List.of(), List.of())).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(dto);

        var update = controller.updateComment(commentId, CommentUpdateRequest.builder().content("u").attachmentIds(List.of()).removedAttachmentIds(List.of()).build());
        assertEquals(HttpStatus.OK, update.getStatusCode());
        var delete = controller.deleteComment(commentId);
        assertEquals(HttpStatus.NO_CONTENT, delete.getStatusCode());
        verify(commentService).delete(commentId);
    }

    @Test
    void addRoundCommentShouldReturnCreated() {
        UUID taskId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        Comment comment = Comment.builder().id(UUID.randomUUID()).content("c").build();
        CommentResponse dto = CommentResponse.builder().id(comment.getId()).content("c").build();
        when(commentService.addCommentToRound(taskId, roundId, "c")).thenReturn(comment);
        when(commentMapper.toResponse(comment)).thenReturn(dto);
        var response = controller.addRoundComment(taskId, roundId, CommentCreateRequest.builder().content("c").build());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}

