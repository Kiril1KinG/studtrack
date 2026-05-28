package ru.diploma.studtrack.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import ru.diploma.studtrack.dto.request.CommentUpdateRequest;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.service.CommentService;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebCommentControllerTest {

    @Mock
    private CommentService commentService;
    @Mock
    private TaskService taskService;
    @Mock
    private ProjectService projectService;
    @Mock
    private WebErrorMessageService webErrorMessageService;

    private WebCommentController controller;
    private UUID taskId;
    private UUID commentId;

    @BeforeEach
    void setUp() {
        controller = new WebCommentController(commentService, taskService, projectService, webErrorMessageService);
        taskId = UUID.randomUUID();
        commentId = UUID.randomUUID();
    }

    @Test
    void addCommentShouldReturnSingleCommentFragment() {
        Model model = new ExtendedModelMap();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        Comment comment = Comment.builder().id(commentId).task(task).content("ok").build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(commentService.addCommentToTask(taskId, "ok", java.util.List.of())).thenReturn(comment);

        String view = controller.addComment(taskId, "ok", null, model);

        assertEquals("fragments/comments :: singleComment", view);
        assertEquals(comment, model.getAttribute("comment"));
        verify(projectService).checkMembership(task.getProject().getId());
    }

    @Test
    void addCommentShouldReturnOperationErrorOnException() {
        Model model = new ExtendedModelMap();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(commentService.addCommentToTask(taskId, "ok", java.util.List.of()))
                .thenThrow(new IllegalArgumentException("fail"));
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");

        String view = controller.addComment(taskId, "ok", null, model);

        assertEquals("fragments/comments :: operationError", view);
        assertEquals("friendly", model.getAttribute("operationErrorMessage"));
    }

    @Test
    void updateCommentShouldUseAttachmentsAndReturnSingleComment() {
        Model model = new ExtendedModelMap();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        Comment existing = Comment.builder().id(commentId).task(task).build();
        Comment updated = Comment.builder().id(commentId).task(task).content("new").build();
        when(commentService.findById(commentId)).thenReturn(existing);
        when(commentService.updateContent(commentId, "new", java.util.List.of(), java.util.List.of())).thenReturn(updated);

        CommentUpdateRequest request = CommentUpdateRequest.builder()
                .content("new")
                .build();

        String view = controller.updateComment(commentId, request, model);

        assertEquals("fragments/comments :: singleComment", view);
        assertEquals(updated, model.getAttribute("comment"));
    }
}

