package ru.diploma.studtrack.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import ru.diploma.studtrack.dto.response.AttachmentResponse;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.service.TaskAttachmentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class AttachmentController {

    private final TaskAttachmentService taskAttachmentService;

    @GetMapping("/{taskId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> getAttachments(@PathVariable UUID taskId) {
        List<AttachmentResponse> response = taskAttachmentService.getAttachments(taskId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{taskId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> upload(@PathVariable UUID taskId,
                                                           @RequestParam("files") List<MultipartFile> files) {
        List<AttachmentResponse> created = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> toResponse(taskAttachmentService.addAttachment(taskId, file)))
                .toList();
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID attachmentId) {
        taskAttachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/attachments/{attachmentId}/url")
    public ResponseEntity<String> getDownloadUrl(@PathVariable UUID attachmentId) {
        return ResponseEntity.ok(taskAttachmentService.getDownloadUrl(attachmentId));
    }

    private AttachmentResponse toResponse(TaskAttachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .taskId(attachment.getTask().getId())
                .uploadedById(attachment.getUploadedBy().getId())
                .uploadedByName(attachment.getUploadedBy().getFullName())
                .originalName(attachment.getOriginalName())
                .contentType(attachment.getContentType())
                .size(attachment.getSize())
                .uploadedAt(attachment.getUploadedAt())
                .build();
    }
}
