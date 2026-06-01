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
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.List;
import java.util.UUID;

@Slf4j
/**
 * Обрабатывает веб-операции с комментариями задач и замечаний.
 */
@Controller
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class WebCommentController {

    /**
     * Предоставляет операции создания, изменения и удаления комментариев.
     */
    private final CommentService commentService;
    /**
     * Предоставляет операции чтения задач.
     */
    private final TaskService taskService;
    /**
     * Проверяет доступ пользователя к проекту.
     */
    private final ProjectService projectService;
    /**
     * Преобразует исключения в пользовательские сообщения для UI.
     */
    private final WebErrorMessageService webErrorMessageService;

    /**
     * Добавляет комментарий к задаче и возвращает HTML-фрагмент одного комментария.
     *
     * @param taskId идентификатор задачи
     * @param content текст комментария
     * @param attachmentIds идентификаторы прикреплённых файлов
     * @param model модель представления
     * @return имя фрагмента комментария или фрагмента ошибки
     */
    @PostMapping("/{taskId}/comments")
    public String addComment(@PathVariable UUID taskId,
                             @RequestParam String content,
                             @RequestParam(name = "attachmentIds", required = false) List<UUID> attachmentIds,
                             Model model) {
        return executeOperation(
                model,
                "Ошибка добавления комментария в задачу " + taskId,
                () -> {
                    ensureTaskAccessible(taskId);
                    Comment comment = commentService.addCommentToTask(taskId, content, safeIds(attachmentIds));
                    model.addAttribute("comment", comment);
                    return "fragments/comments :: singleComment";
                }
        );
    }

    /**
     * Удаляет комментарий и возвращает пустой ответ для HTMX.
     *
     * @param commentId идентификатор комментария
     * @return пустая строка
     */
    @DeleteMapping("/comments/{commentId}")
    @ResponseBody
    public String deleteComment(@PathVariable UUID commentId) {
        commentService.delete(commentId);
        return "";
    }

    /**
     * Возвращает фрагмент формы редактирования комментария.
     *
     * @param commentId идентификатор комментария
     * @param model модель представления
     * @return имя фрагмента формы редактирования
     */
    @GetMapping("/comments/{commentId}/edit")
    public String editComment(@PathVariable UUID commentId, Model model) {
        Comment comment = getAccessibleComment(commentId);
        model.addAttribute("comment", comment);
        model.addAttribute("taskId", comment.getTask().getId());
        return "fragments/comments :: editComment";
    }

    /**
     * Отменяет редактирование комментария и возвращает его обычный вид.
     *
     * @param commentId идентификатор комментария
     * @param model модель представления
     * @return имя фрагмента комментария
     */
    @GetMapping("/comments/{commentId}/cancel")
    public String cancelEditComment(@PathVariable UUID commentId, Model model) {
        Comment comment = getAccessibleComment(commentId);
        model.addAttribute("comment", comment);
        return "fragments/comments :: singleComment";
    }

    /**
     * Обновляет комментарий и возвращает обновлённый HTML-фрагмент.
     *
     * @param commentId идентификатор комментария
     * @param request данные обновления комментария
     * @param model модель представления
     * @return имя фрагмента комментария или фрагмента ошибки
     */
    @PutMapping("/comments/{commentId}")
    public String updateComment(@PathVariable UUID commentId,
                                @Valid @ModelAttribute CommentUpdateRequest request,
                                Model model) {
        return executeOperation(
                model,
                "Ошибка обновления комментария " + commentId,
                () -> {
                    getAccessibleComment(commentId);
                    Comment comment = commentService.updateContent(
                            commentId,
                            request.getContent(),
                            safeIds(request.getAttachmentIds()),
                            safeIds(request.getRemovedAttachmentIds())
                    );
                    model.addAttribute("comment", comment);
                    return "fragments/comments :: singleComment";
                }
        );
    }

    /**
     * Возвращает список комментариев для замечания.
     *
     * @param crId идентификатор замечания
     * @param model модель представления
     * @return имя фрагмента списка комментариев замечания
     */
    @GetMapping("/change-requests/{crId}/comments")
    public String getCrComments(@PathVariable UUID crId, Model model) {
        addCrCommentsToModel(crId, model);
        return "fragments/comments :: crCommentList";
    }

    /**
     * Добавляет комментарий к замечанию и возвращает обновлённый список.
     *
     * @param crId идентификатор замечания
     * @param content текст комментария
     * @param attachmentIds идентификаторы прикреплённых файлов
     * @param model модель представления
     * @return имя фрагмента списка комментариев замечания
     */
    @PostMapping("/change-requests/{crId}/comments")
    public String addCrComment(@PathVariable UUID crId,
                               @RequestParam String content,
                               @RequestParam(name = "attachmentIds", required = false) List<UUID> attachmentIds,
                               Model model) {
        executeCrOperation(
                crId,
                model,
                () -> commentService.addCommentToChangeRequest(crId, content, safeIds(attachmentIds))
        );
        addCrCommentsToModel(crId, model);
        return "fragments/comments :: crCommentList";
    }

    /**
     * Проверяет доступ к задаче по членству пользователя в проекте.
     *
     * @param taskId идентификатор задачи
     */
    private void ensureTaskAccessible(UUID taskId) {
        var task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
    }

    /**
     * Возвращает комментарий и проверяет доступ к его проекту.
     *
     * @param commentId идентификатор комментария
     * @return доступный комментарий
     */
    private Comment getAccessibleComment(UUID commentId) {
        Comment comment = commentService.findById(commentId);
        projectService.checkMembership(comment.getTask().getProject().getId());
        return comment;
    }

    /**
     * Возвращает неизменяемый список идентификаторов без null.
     *
     * @param ids исходный список идентификаторов
     * @return исходный список или пустой список, если значение null
     */
    private List<UUID> safeIds(List<UUID> ids) {
        return ids != null ? ids : List.of();
    }

    /**
     * Заполняет модель комментариями выбранного замечания.
     *
     * @param crId идентификатор замечания
     * @param model модель представления
     */
    private void addCrCommentsToModel(UUID crId, Model model) {
        List<Comment> comments = commentService.getByChangeRequest(crId);
        model.addAttribute("comments", comments);
        model.addAttribute("crId", crId);
    }

    /**
     * Выполняет действие с комментарием с единым обработчиком ошибок.
     *
     * @param model модель представления
     * @param logMessage сообщение для логирования
     * @param action действие, возвращающее имя фрагмента
     * @return имя фрагмента результата или ошибки
     */
    private String executeOperation(Model model,
                                    String logMessage,
                                    java.util.function.Supplier<String> action) {
        try {
            return action.get();
        } catch (Exception e) {
            log.warn("{}: {}", logMessage, e.getMessage());
            model.addAttribute(
                    "operationErrorMessage",
                    webErrorMessageService.resolve(e, "Не удалось выполнить операцию с комментарием. Попробуйте еще раз.")
            );
            return "fragments/comments :: operationError";
        }
    }

    /**
     * Выполняет действие с комментарием замечания с обработкой ошибок.
     *
     * @param crId идентификатор замечания
     * @param model модель представления
     * @param action выполняемое действие
     */
    private void executeCrOperation(UUID crId,
                                    Model model,
                                    Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Ошибка добавления комментария к замечанию {}: {}", crId, e.getMessage());
            model.addAttribute(
                    "crErrorMessage",
                    webErrorMessageService.resolve(e, "Не удалось добавить комментарий к замечанию. Попробуйте еще раз.")
            );
        }
    }
}