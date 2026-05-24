package ru.diploma.studtrack.controller.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import ru.diploma.studtrack.dto.request.CommentUpdateRequest;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.service.CommentService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.ProjectService;

import java.util.List;
import java.util.UUID;

@Slf4j
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class WebCommentController {

    private final CommentService commentService;
    private final TaskService taskService;
    private final ProjectService projectService;

    @PostMapping("/{taskId}/comments")
    public String addComment(@PathVariable UUID taskId,
                             @RequestParam String content,
                             @RequestParam(name = "attachmentIds", required = false) List<UUID> attachmentIds,
                             Model model) {
        var task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        Comment comment = commentService.addCommentToTask(taskId, content, attachmentIds != null ? attachmentIds : List.of());
        model.addAttribute("comment", comment);
        return "fragments/comments :: singleComment";
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseBody
    public String deleteComment(@PathVariable UUID commentId) {
        commentService.delete(commentId);
        return "";
    }

    @GetMapping("/comments/{commentId}/edit")
    public String editComment(@PathVariable UUID commentId, Model model) {
        Comment comment = commentService.findById(commentId);
        projectService.checkMembership(comment.getTask().getProject().getId());
        model.addAttribute("comment", comment);
        model.addAttribute("taskId", comment.getTask().getId());
        return "fragments/comments :: editComment";
    }

    @GetMapping("/comments/{commentId}/cancel")
    public String cancelEditComment(@PathVariable UUID commentId, Model model) {
        Comment comment = commentService.findById(commentId);
        projectService.checkMembership(comment.getTask().getProject().getId());
        model.addAttribute("comment", comment);
        return "fragments/comments :: singleComment";
    }

    @PutMapping("/comments/{commentId}")
    public String updateComment(@PathVariable UUID commentId,
                                @Valid @ModelAttribute CommentUpdateRequest request,
                                Model model) {
        Comment existing = commentService.findById(commentId);
        Comment comment = commentService.updateContent(
                commentId,
                request.getContent(),
                request.getAttachmentIds(),
                request.getRemovedAttachmentIds()
        );
        model.addAttribute("comment", comment);
        return "fragments/comments :: singleComment";
    }

    @GetMapping("/change-requests/{crId}/comments")
    public String getCrComments(@PathVariable UUID crId, Model model) {
        List<Comment> comments = commentService.getByChangeRequest(crId);
        model.addAttribute("comments", comments);
        model.addAttribute("crId", crId);
        return "fragments/comments :: crCommentList";
    }

    @PostMapping("/change-requests/{crId}/comments")
    public String addCrComment(@PathVariable UUID crId,
                               @RequestParam String content,
                               @RequestParam(name = "attachmentIds", required = false) List<UUID> attachmentIds,
                               Model model) {
        commentService.addCommentToChangeRequest(crId, content, attachmentIds != null ? attachmentIds : List.of());
        List<Comment> comments = commentService.getByChangeRequest(crId);
        model.addAttribute("comments", comments);
        model.addAttribute("crId", crId);
        return "fragments/comments :: crCommentList";
    }
}