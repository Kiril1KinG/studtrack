package ru.diploma.studtrack.service;

import org.springframework.stereotype.Service;
import ru.diploma.studtrack.model.ArtifactType;
import ru.diploma.studtrack.model.TaskAttachment;

@Service
/**
 * Формирует сериализуемое значение вложения для истории изменений задачи.
 */
public class AttachmentHistoryValueService {

    /**
     * Преобразует вложение в строковый формат для хранения в истории.
     *
     * @param attachment вложение задачи
     * @return строковое значение формата FILE::name или LINK::title/url
     */
    public String historyValueFor(TaskAttachment attachment) {
        if (attachment.getType() == ArtifactType.LINK) {
            if (attachment.getLinkTitle() != null && !attachment.getLinkTitle().isBlank()) {
                return "LINK::" + attachment.getLinkTitle();
            }
            return "LINK::" + attachment.getLinkUrl();
        }
        return "FILE::" + attachment.getOriginalName();
    }
}
