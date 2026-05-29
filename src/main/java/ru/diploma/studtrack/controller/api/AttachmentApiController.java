package ru.diploma.studtrack.controller.api;

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

/**
 * Предоставляет REST-эндпоинты для управления вложениями и ссылками задачи.
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class AttachmentApiController {

    /**
     * Выполняет бизнес-операции с файловыми вложениями и ссылками задачи.
     */
    private final TaskAttachmentService taskAttachmentService;

    /**
     * Возвращает все артефакты, прикреплённые к задаче.
     *
     * @param taskId идентификатор задачи
     * @return список вложений и ссылок задачи
     */
    @GetMapping("/{taskId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> getAttachments(@PathVariable UUID taskId) {
        List<AttachmentResponse> response = taskAttachmentService.getTaskArtifacts(taskId).stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    /**
     * Загружает файлы и прикрепляет их к задаче.
     *
     * @param taskId идентификатор задачи
     * @param files загруженные файлы
     * @return список созданных вложений
     */
    @PostMapping("/{taskId}/attachments")
    public ResponseEntity<List<AttachmentResponse>> upload(@PathVariable UUID taskId,
                                                           @RequestParam("files") List<MultipartFile> files) {
        List<AttachmentResponse> created = files.stream()
                .filter(file -> file != null && !file.isEmpty())
                .map(file -> toResponse(taskAttachmentService.addAttachment(taskId, file)))
                .toList();
        return ResponseEntity.ok(created);
    }

    /**
     * Добавляет внешнюю ссылку как артефакт задачи.
     *
     * @param taskId идентификатор задачи
     * @param url целевой URL
     * @param title необязательный заголовок ссылки
     * @return созданная ссылка
     */
    @PostMapping("/{taskId}/links")
    public ResponseEntity<AttachmentResponse> addLink(@PathVariable UUID taskId,
                                                      @RequestParam("url") String url,
                                                      @RequestParam(name = "title", required = false) String title) {
        TaskAttachment created = taskAttachmentService.addLink(taskId, url, title);
        return ResponseEntity.ok(toResponse(created));
    }

    /**
     * Удаляет вложение или ссылку по идентификатору.
     *
     * @param attachmentId идентификатор вложения
     * @return пустой ответ со статусом 204
     */
    @DeleteMapping("/attachments/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID attachmentId) {
        taskAttachmentService.deleteAttachment(attachmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Генерирует временный URL для скачивания вложения.
     *
     * @param attachmentId идентификатор вложения
     * @return временный URL для скачивания
     */
    @GetMapping("/attachments/{attachmentId}/url")
    public ResponseEntity<String> getDownloadUrl(@PathVariable UUID attachmentId) {
        return ResponseEntity.ok(taskAttachmentService.getDownloadUrl(attachmentId));
    }

    /**
     * Преобразует доменную модель вложения в API-ответ.
     *
     * @param attachment сущность вложения задачи
     * @return сериализованный ответ вложения
     */
    private AttachmentResponse toResponse(TaskAttachment attachment) {
        return AttachmentResponse.builder()
                .id(attachment.getId())
                .taskId(attachment.getTask().getId())
                .uploadedById(attachment.getUploadedBy().getId())
                .uploadedByName(attachment.getUploadedBy().getFullName())
                .originalName(attachment.getOriginalName())
                .contentType(attachment.getContentType())
                .size(attachment.getSize())
                .type(attachment.getType())
                .linkUrl(attachment.getLinkUrl())
                .linkTitle(attachment.getLinkTitle())
                .uploadedAt(attachment.getUploadedAt())
                .build();
    }
}
