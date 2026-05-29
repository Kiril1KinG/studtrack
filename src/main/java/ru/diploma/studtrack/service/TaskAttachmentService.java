package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Sort;
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
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * Управляет вложениями и ссылками задач, включая загрузку в MinIO.
 */
public class TaskAttachmentService {

    /**
     * Репозиторий вложений задач.
     */
    private final TaskAttachmentRepository taskAttachmentRepository;
    /**
     * Сервис задач.
     */
    private final TaskService taskService;
    /**
     * Сервис пользователей.
     */
    private final UserService userService;
    /**
     * Сервис проверки доступа к проекту.
     */
    private final ProjectService projectService;
    /**
     * Сервис работы с файловым хранилищем MinIO.
     */
    private final MinioStorageService minioStorageService;

    @Transactional
    /**
     * Загружает файл и создаёт вложение задачи.
     *
     * @param taskId идентификатор задачи
     * @param file файл для загрузки
     * @return созданное вложение
     */
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
    /**
     * Добавляет ссылку как артефакт задачи.
     *
     * @param taskId идентификатор задачи
     * @param url URL ссылки
     * @param title заголовок ссылки
     * @return созданное вложение типа LINK
     */
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

    /**
     * Возвращает файловые вложения задачи.
     *
     * @param taskId идентификатор задачи
     * @return список файловых вложений
     */
    public List<TaskAttachment> getAttachments(UUID taskId) {
        return getFileAttachments(taskId);
    }

    /**
     * Возвращает только файловые вложения задачи.
     *
     * @param taskId идентификатор задачи
     * @return список файловых вложений
     */
    public List<TaskAttachment> getFileAttachments(UUID taskId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        return taskAttachmentRepository.findByTaskIdAndCommentIsNullAndTypeOrderByUploadedAtDesc(taskId, ArtifactType.FILE);
    }

    /**
     * Возвращает все артефакты задачи (файлы и ссылки), исключая вложения комментариев.
     *
     * @param taskId идентификатор задачи
     * @return список артефактов
     */
    public List<TaskAttachment> getTaskArtifacts(UUID taskId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        return taskAttachmentRepository.findByTaskIdAndCommentIsNullOrderByUploadedAtDesc(taskId);
    }

    /**
     * Возвращает артефакты проекта с сортировкой для вкладки репозитория.
     *
     * @param projectId идентификатор проекта
     * @param sortKey ключ сортировки
     * @return список артефактов проекта
     */
    public List<TaskAttachment> getProjectArtifacts(UUID projectId, String sortKey) {
        projectService.checkMembership(projectId);
        Sort sort = switch ((sortKey == null ? "" : sortKey.toLowerCase(Locale.ROOT))) {
            case "task" -> Sort.by(Sort.Order.asc("task.title"), Sort.Order.desc("uploadedAt"));
            case "type" -> Sort.by(Sort.Order.asc("type"), Sort.Order.desc("uploadedAt"));
            case "oldest" -> Sort.by(Sort.Order.asc("uploadedAt"));
            case "newest" -> Sort.by(Sort.Order.desc("uploadedAt"));
            default -> Sort.by(Sort.Order.desc("uploadedAt"));
        };
        return taskAttachmentRepository.findByTaskProjectIdAndCommentIsNull(projectId, sort);
    }

    /**
     * Возвращает вложение по идентификатору.
     *
     * @param attachmentId идентификатор вложения
     * @return найденное вложение
     */
    public TaskAttachment findById(UUID attachmentId) {
        return taskAttachmentRepository.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Вложение", attachmentId));
    }

    @Transactional
    /**
     * Привязывает загруженные файлы задачи к комментарию.
     *
     * @param taskId идентификатор задачи
     * @param comment комментарий
     * @param attachmentIds идентификаторы вложений
     * @return список привязанных вложений
     */
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
    /**
     * Удаляет вложение или ссылку с проверкой прав доступа.
     *
     * @param attachmentId идентификатор вложения
     */
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
    /**
     * Удаляет выбранные вложения комментария.
     *
     * @param comment комментарий
     * @param attachmentIds идентификаторы вложений для удаления
     */
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

    /**
     * Генерирует временный URL для скачивания файла-вложения.
     *
     * @param attachmentId идентификатор вложения
     * @return временный URL
     */
    public String getDownloadUrl(UUID attachmentId) {
        TaskAttachment attachment = findById(attachmentId);
        projectService.checkMembership(attachment.getTask().getProject().getId());
        return minioStorageService.getPresignedDownloadUrl(attachment.getStoredKey(), Duration.ofMinutes(15));
    }

    /**
     * Извлекает расширение файла.
     *
     * @param fileName имя файла
     * @return расширение с точкой или пустая строка
     */
    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int idx = fileName.lastIndexOf('.');
        return idx >= 0 ? fileName.substring(idx) : "";
    }

    /**
     * Нормализует и валидирует URL.
     *
     * @param raw исходная строка URL
     * @return нормализованный URL или null при невалидном значении
     */
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
