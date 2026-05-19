package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskAttachmentRepository;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskAttachmentService {

    private final TaskAttachmentRepository taskAttachmentRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final ProjectService projectService;
    private final MinioStorageService minioStorageService;

    @Transactional
    public TaskAttachment addAttachment(UUID taskId, MultipartFile file) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        User currentUser = userService.getCurrentUser();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Файл не выбран");
        }

        String extension = extractExtension(file.getOriginalFilename());
        String key = "tasks/" + taskId + "/" + UUID.randomUUID() + extension;

        try {
            minioStorageService.upload(
                    key,
                    file.getInputStream(),
                    file.getContentType(),
                    file.getSize()
            );
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить файл в хранилище", e);
        }

        TaskAttachment attachment = TaskAttachment.builder()
                .task(task)
                .uploadedBy(currentUser)
                .originalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file")
                .storedKey(key)
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();
        return taskAttachmentRepository.save(attachment);
    }

    public List<TaskAttachment> getAttachments(UUID taskId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        return taskAttachmentRepository.findByTaskIdOrderByUploadedAtDesc(taskId);
    }

    public TaskAttachment findById(UUID attachmentId) {
        return taskAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Вложение", attachmentId));
    }

    @Transactional
    public void deleteAttachment(UUID attachmentId) {
        TaskAttachment attachment = findById(attachmentId);
        Task task = attachment.getTask();
        projectService.checkMembership(task.getProject().getId());
        UUID currentUserId = userService.getCurrentUserId();
        boolean isOwner = projectService.isOwner(task.getProject().getId(), currentUserId);
        boolean isAuthor = attachment.getUploadedBy().getId().equals(currentUserId);

        if (!isOwner && !isAuthor) {
            throw new AccessDeniedException("Удалить файл может только автор загрузки или владелец проекта");
        }

        minioStorageService.delete(attachment.getStoredKey());
        taskAttachmentRepository.delete(attachment);
    }

    public String getDownloadUrl(UUID attachmentId) {
        TaskAttachment attachment = findById(attachmentId);
        projectService.checkMembership(attachment.getTask().getProject().getId());
        return minioStorageService.getPresignedDownloadUrl(attachment.getStoredKey(), Duration.ofMinutes(15));
    }

    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx) : "";
    }
}
