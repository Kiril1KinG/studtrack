package ru.diploma.studtrack.service;

import org.junit.jupiter.api.Test;
import ru.diploma.studtrack.model.ArtifactType;
import ru.diploma.studtrack.model.TaskAttachment;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AttachmentHistoryValueServiceTest {

    private final AttachmentHistoryValueService service = new AttachmentHistoryValueService();

    @Test
    void historyValueForShouldReturnFilePrefixForFile() {
        TaskAttachment attachment = TaskAttachment.builder()
                .type(ArtifactType.FILE)
                .originalName("report.pdf")
                .build();

        assertEquals("FILE::report.pdf", service.historyValueFor(attachment));
    }

    @Test
    void historyValueForShouldPreferLinkTitle() {
        TaskAttachment attachment = TaskAttachment.builder()
                .type(ArtifactType.LINK)
                .linkTitle("Docs")
                .linkUrl("https://example.com")
                .build();

        assertEquals("LINK::Docs", service.historyValueFor(attachment));
    }

    @Test
    void historyValueForShouldFallbackToLinkUrlWhenTitleBlank() {
        TaskAttachment attachment = TaskAttachment.builder()
                .type(ArtifactType.LINK)
                .linkTitle(" ")
                .linkUrl("https://example.com")
                .build();

        assertEquals("LINK::https://example.com", service.historyValueFor(attachment));
    }
}

