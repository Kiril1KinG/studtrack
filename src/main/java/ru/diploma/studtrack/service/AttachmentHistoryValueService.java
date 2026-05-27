package ru.diploma.studtrack.service;

import org.springframework.stereotype.Service;
import ru.diploma.studtrack.model.ArtifactType;
import ru.diploma.studtrack.model.TaskAttachment;

@Service
public class AttachmentHistoryValueService {

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
