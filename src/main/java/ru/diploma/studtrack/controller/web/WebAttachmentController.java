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
import ru.diploma.studtrack.service.AttachmentHistoryValueService;
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
    private final AttachmentHistoryValueService attachmentHistoryValueService;

    @GetMapping("/{taskId}/attachments")
    public String getAttachments(@PathVariable UUID taskId, Model model) {
        Task task = getAccessibleTask(taskId);
        List<TaskAttachment> attachments = taskAttachmentService.getTaskArtifacts(taskId);
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
        executeAttachmentOperation(taskId, model, () -> {
            if (files == null) {
                return;
            }
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    TaskAttachment created = taskAttachmentService.addAttachment(taskId, file);
                    taskHistoryService.recordFieldChange(
                            created.getTask(),
                            userService.getCurrentUser(),
                            "attachments",
                            null,
                            attachmentHistoryValueService.historyValueFor(created)
                    );
                }
            }
        });
        return getAttachments(taskId, model);
    }

    @PostMapping("/{taskId}/links")
    public String addLink(@PathVariable UUID taskId,
                          @RequestParam String url,
                          @RequestParam(required = false) String title,
                          Model model) {
        executeAttachmentOperation(taskId, model, () -> {
            TaskAttachment created = taskAttachmentService.addLink(taskId, url, title);
            taskHistoryService.recordFieldChange(
                    created.getTask(),
                    userService.getCurrentUser(),
                    "attachments",
                    null,
                    attachmentHistoryValueService.historyValueFor(created)
            );
        });
        return getAttachments(taskId, model);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public String deleteAttachment(@PathVariable UUID attachmentId,
                                   @RequestParam(required = false) UUID taskId,
                                   Model model) {
        return deleteAndReload(attachmentId, taskId, model);
    }

    @PostMapping("/attachments/{attachmentId}")
    public String deleteAttachmentPostFallback(@PathVariable UUID attachmentId,
                                               @RequestParam(required = false) UUID taskId,
                                               Model model) {
        return deleteAndReload(attachmentId, taskId, model);
    }

    @GetMapping("/attachments/{attachmentId}/url")
    public RedirectView downloadAttachment(@PathVariable UUID attachmentId) {
        String url = taskAttachmentService.getDownloadUrl(attachmentId);
        return new RedirectView(url);
    }

    private String deleteAndReload(UUID attachmentId, UUID taskId, Model model) {
        try {
            TaskAttachment attachment = taskAttachmentService.findById(attachmentId);
            if (taskId == null) {
                taskId = attachment.getTask().getId();
            }
            taskHistoryService.recordFieldChange(
                    attachment.getTask(),
                    userService.getCurrentUser(),
                    "attachments",
                    attachmentHistoryValueService.historyValueFor(attachment),
                    null
            );
            taskAttachmentService.deleteAttachment(attachmentId);
            return getAttachments(taskId, model);
        } catch (Exception e) {
            if (taskId != null) {
                model.addAttribute("attachmentErrorMessage", e.getMessage());
                return getAttachments(taskId, model);
            }
            model.addAttribute("attachmentErrorMessage", e.getMessage());
            return "fragments/task-attachments :: attachmentList";
        }
    }

    private Task getAccessibleTask(UUID taskId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        return task;
    }

    private void executeAttachmentOperation(UUID taskId,
                                            Model model,
                                            Runnable action) {
        try {
            getAccessibleTask(taskId);
            action.run();
        } catch (Exception e) {
            model.addAttribute("attachmentErrorMessage", e.getMessage());
        }
    }
}
