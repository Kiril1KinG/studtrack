package ru.diploma.studtrack.controller.web;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.multipart.MultipartFile;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskAttachmentService;
import ru.diploma.studtrack.service.TaskHistoryService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.UserService;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class WebAttachmentController {

    private final TaskAttachmentService taskAttachmentService;
    private final UserService userService;
    private final TaskService taskService;
    private final ProjectService projectService;
    private final TaskHistoryService taskHistoryService;

    @GetMapping("/{taskId}/attachments")
    public String getAttachments(@PathVariable UUID taskId, Model model) {
        Task task = taskService.findById(taskId);
        List<TaskAttachment> attachments = taskAttachmentService.getAttachments(taskId);
        UUID currentUserId = userService.getCurrentUserId();
        model.addAttribute("taskId", taskId);
        model.addAttribute("attachments", attachments);
        model.addAttribute("currentUserId", currentUserId);
        model.addAttribute("isOwner", projectService.isOwner(task.getProject().getId(), currentUserId));
        return "fragments/task-attachments :: attachmentList";
    }

    @PostMapping("/{taskId}/attachments")
    public String uploadAttachment(@PathVariable UUID taskId,
                                   @RequestParam("files") List<MultipartFile> files,
                                   Model model) {
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    TaskAttachment created = taskAttachmentService.addAttachment(taskId, file);
                    Task task = created.getTask();
                    taskHistoryService.recordFieldChange(
                            task,
                            userService.getCurrentUser(),
                            "attachments",
                            null,
                            created.getOriginalName()
                    );
                }
            }
        }
        return getAttachments(taskId, model);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public String deleteAttachment(@PathVariable UUID attachmentId,
                                   @RequestParam(required = false) UUID taskId,
                                   Model model) {
        if (taskId == null) {
            taskId = taskAttachmentService.findById(attachmentId).getTask().getId();
        }
        taskAttachmentService.deleteAttachment(attachmentId);
        return getAttachments(taskId, model);
    }

    @GetMapping("/attachments/{attachmentId}/url")
    public RedirectView downloadAttachment(@PathVariable UUID attachmentId) {
        String url = taskAttachmentService.getDownloadUrl(attachmentId);
        return new RedirectView(url);
    }
}
