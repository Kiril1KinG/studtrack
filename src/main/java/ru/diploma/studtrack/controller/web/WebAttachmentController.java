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
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.List;
import java.util.UUID;

/**
 * Обрабатывает веб-операции с вложениями задачи.
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class WebAttachmentController {

    /**
     * Предоставляет операции загрузки и удаления вложений.
     */
    private final TaskAttachmentService taskAttachmentService;
    /**
     * Предоставляет данные текущего пользователя.
     */
    private final UserService userService;
    /**
     * Предоставляет операции чтения задач.
     */
    private final TaskService taskService;
    /**
     * Проверяет доступ пользователя к проекту и права владельца.
     */
    private final ProjectService projectService;
    /**
     * Сохраняет историю изменений задачи.
     */
    private final TaskHistoryService taskHistoryService;
    /**
     * Формирует читаемое значение вложения для истории задачи.
     */
    private final AttachmentHistoryValueService attachmentHistoryValueService;
    /**
     * Преобразует исключения в пользовательские сообщения для UI.
     */
    private final WebErrorMessageService webErrorMessageService;

    /**
     * Возвращает фрагмент списка вложений задачи.
     *
     * @param taskId идентификатор задачи
     * @param model модель представления
     * @return имя фрагмента списка вложений
     */
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

    /**
     * Загружает файлы в задачу и возвращает обновлённый список вложений.
     *
     * @param taskId идентификатор задачи
     * @param files файлы для загрузки
     * @param model модель представления
     * @return имя фрагмента списка вложений
     */
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
                    recordAttachmentAdded(created);
                }
            }
        });
        return getAttachments(taskId, model);
    }

    /**
     * Добавляет ссылку во вложения задачи и возвращает обновлённый список.
     *
     * @param taskId идентификатор задачи
     * @param url URL ссылки
     * @param title заголовок ссылки
     * @param model модель представления
     * @return имя фрагмента списка вложений
     */
    @PostMapping("/{taskId}/links")
    public String addLink(@PathVariable UUID taskId,
                          @RequestParam String url,
                          @RequestParam(required = false) String title,
                          Model model) {
        executeAttachmentOperation(taskId, model, () -> {
            TaskAttachment created = taskAttachmentService.addLink(taskId, url, title);
            recordAttachmentAdded(created);
        });
        return getAttachments(taskId, model);
    }

    /**
     * Удаляет вложение и возвращает обновлённый список.
     *
     * @param attachmentId идентификатор вложения
     * @param taskId идентификатор задачи
     * @param model модель представления
     * @return имя фрагмента списка вложений
     */
    @DeleteMapping("/attachments/{attachmentId}")
    public String deleteAttachment(@PathVariable UUID attachmentId,
                                   @RequestParam(required = false) UUID taskId,
                                   Model model) {
        return deleteAndReload(attachmentId, taskId, model);
    }

    /**
     * Обрабатывает удаление вложения через POST как fallback для клиентов без DELETE.
     *
     * @param attachmentId идентификатор вложения
     * @param taskId идентификатор задачи
     * @param model модель представления
     * @return имя фрагмента списка вложений
     */
    @PostMapping("/attachments/{attachmentId}")
    public String deleteAttachmentPostFallback(@PathVariable UUID attachmentId,
                                               @RequestParam(required = false) UUID taskId,
                                               Model model) {
        return deleteAndReload(attachmentId, taskId, model);
    }

    /**
     * Перенаправляет пользователя на временный URL скачивания вложения.
     *
     * @param attachmentId идентификатор вложения
     * @return redirect на URL скачивания
     */
    @GetMapping("/attachments/{attachmentId}/url")
    public RedirectView downloadAttachment(@PathVariable UUID attachmentId) {
        String url = taskAttachmentService.getDownloadUrl(attachmentId);
        return new RedirectView(url);
    }

    /**
     * Удаляет вложение, фиксирует историю и перезагружает список вложений.
     *
     * @param attachmentId идентификатор вложения
     * @param taskId идентификатор задачи
     * @param model модель представления
     * @return имя фрагмента списка вложений
     */
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
            String message = webErrorMessageService.resolve(e, "Не удалось удалить вложение. Попробуйте еще раз.");
            if (taskId != null) {
                return getAttachmentsWithError(taskId, model, message);
            }
            model.addAttribute("attachmentErrorMessage", message);
            return "fragments/task-attachments :: attachmentList";
        }
    }

    /**
     * Возвращает задачу и проверяет доступ пользователя по членству в проекте.
     *
     * @param taskId идентификатор задачи
     * @return доступная задача
     */
    private Task getAccessibleTask(UUID taskId) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        return task;
    }

    /**
     * Выполняет операцию со вложениями с единым обработчиком ошибок.
     *
     * @param taskId идентификатор задачи
     * @param model модель представления
     * @param action выполняемое действие
     */
    private void executeAttachmentOperation(UUID taskId,
                                            Model model,
                                            Runnable action) {
        try {
            getAccessibleTask(taskId);
            action.run();
        } catch (Exception e) {
            getAttachmentsWithError(
                    taskId,
                    model,
                    webErrorMessageService.resolve(e, "Не удалось выполнить операцию с вложением. Попробуйте еще раз.")
            );
        }
    }

    /**
     * Записывает в историю факт добавления вложения в задачу.
     *
     * @param attachment добавленное вложение
     */
    private void recordAttachmentAdded(TaskAttachment attachment) {
        taskHistoryService.recordFieldChange(
                attachment.getTask(),
                userService.getCurrentUser(),
                "attachments",
                null,
                attachmentHistoryValueService.historyValueFor(attachment)
        );
    }

    /**
     * Добавляет сообщение ошибки и возвращает фрагмент списка вложений.
     *
     * @param taskId идентификатор задачи
     * @param model модель представления
     * @param errorMessage текст ошибки для пользователя
     * @return имя фрагмента списка вложений
     */
    private String getAttachmentsWithError(UUID taskId, Model model, String errorMessage) {
        model.addAttribute("attachmentErrorMessage", errorMessage);
        return getAttachments(taskId, model);
    }
}
