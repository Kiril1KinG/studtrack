package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.ArtifactType;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskAttachmentRepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
                .type(ArtifactType.FILE)
                .originalName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "file")
                .storedKey(key)
                .contentType(file.getContentType())
                .size(file.getSize())
                .build();
        return taskAttachmentRepository.save(attachment);
    }

    @Transactional
    public TaskAttachment addLink(UUID taskId, String url, String title) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        User currentUser = userService.getCurrentUser();

        String normalized = normalizeUrl(url);
        if (normalized == null) {
            throw new IllegalArgumentException("Ссылка должна быть в формате http/https");
        }

        String trimmedTitle = title == null ? null : title.trim();
        TaskAttachment link = TaskAttachment.builder()
                .task(task)
                .uploadedBy(currentUser)
                .type(ArtifactType.LINK)
                .linkUrl(normalized)
                .linkTitle((trimmedTitle == null || trimmedTitle.isBlank()) ? null : trimmedTitle)
                .originalName((trimmedTitle == null || trimmedTitle.isBlank()) ? normalized : trimmedTitle)
                .contentType("text/uri-list")
                .size(0L)
                .build();
        return taskAttachmentRepository.save(link);
    }

    public List<TaskAttachment> getAttachments(UUID taskId) {
        return getFileAttachments(taskId);
    }

    public List<TaskAttachment> getFileAttachments(UUID taskId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        return taskAttachmentRepository.findByTaskIdAndCommentIsNullAndTypeOrderByUploadedAtDesc(taskId, ArtifactType.FILE);
    }

    public List<TaskAttachment> getTaskArtifacts(UUID taskId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        return taskAttachmentRepository.findByTaskIdAndCommentIsNullOrderByUploadedAtDesc(taskId);
    }

    public TaskAttachment findById(UUID attachmentId) {
        return taskAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Вложение", attachmentId));
    }

    @Transactional
    public List<TaskAttachment> attachToComment(UUID taskId, Comment comment, List<UUID> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<TaskAttachment> attachments = taskAttachmentRepository.findByIdIn(attachmentIds);
        if (attachments.size() != attachmentIds.size()) {
            throw new NotFoundException("Некоторые вложения не найдены");
        }

        UUID currentUserId = userService.getCurrentUserId();
        for (TaskAttachment attachment : attachments) {
            if (!attachment.getTask().getId().equals(taskId)) {
                throw new InvalidStateException("Нельзя привязать вложение", "вложение относится к другой задаче", "прикрепите файл в текущей задаче");
            }
            if (attachment.getType() != ArtifactType.FILE) {
                throw new InvalidStateException("Нельзя привязать вложение", "к комментарию можно прикрепить только файл", "используйте файл вместо ссылки");
            }
            if (attachment.getComment() != null) {
                throw new InvalidStateException("Нельзя привязать вложение", "вложение уже прикреплено к другому комментарию", "загрузите новый файл");
            }
            if (!attachment.getUploadedBy().getId().equals(currentUserId)) {
                throw new AccessDeniedException("Привязать можно только свои загруженные вложения");
            }
            attachment.setComment(comment);
            if (comment.getAttachments() != null && comment.getAttachments().stream()
                    .noneMatch(existing -> existing.getId() != null && existing.getId().equals(attachment.getId()))) {
                comment.getAttachments().add(attachment);
            }
        }

        return taskAttachmentRepository.saveAll(attachments);
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

        if (attachment.getType() == ArtifactType.FILE
                && attachment.getStoredKey() != null
                && !attachment.getStoredKey().isBlank()) {
            minioStorageService.delete(attachment.getStoredKey());
        }
        taskAttachmentRepository.delete(attachment);
    }

    @Transactional
    public void deleteCommentAttachments(Comment comment, List<UUID> attachmentIds) {
        if (comment == null || attachmentIds == null || attachmentIds.isEmpty()) {
            return;
        }
        Set<UUID> targetIds = attachmentIds.stream().collect(Collectors.toSet());
        if (comment.getAttachments() == null || comment.getAttachments().isEmpty()) {
            return;
        }

        Iterator<TaskAttachment> iterator = comment.getAttachments().iterator();
        while (iterator.hasNext()) {
            TaskAttachment attachment = iterator.next();
            if (attachment.getId() == null || !targetIds.contains(attachment.getId())) {
                continue;
            }
            iterator.remove();
            attachment.setComment(null);
            if (attachment.getType() == ArtifactType.FILE
                    && attachment.getStoredKey() != null
                    && !attachment.getStoredKey().isBlank()) {
                minioStorageService.delete(attachment.getStoredKey());
            }
            taskAttachmentRepository.delete(attachment);
        }
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

    private String normalizeUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String candidate = raw.trim();
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return null;
            }
            String normalizedScheme = scheme.toLowerCase();
            if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
                return null;
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return null;
            }
            return uri.toString();
        } catch (URISyntaxException ex) {
            return null;
        }
    }
}
