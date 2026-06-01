package ru.diploma.studtrack.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.diploma.studtrack.model.ArtifactType;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.TaskAttachmentService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttachmentApiControllerTest {

    @Mock
    private TaskAttachmentService taskAttachmentService;

    private AttachmentApiController controller;

    @BeforeEach
    void setUp() {
        controller = new AttachmentApiController(taskAttachmentService);
    }

    @Test
    void getAttachmentsShouldMapResponse() {
        UUID taskId = UUID.randomUUID();
        TaskAttachment attachment = TaskAttachment.builder()
                .id(UUID.randomUUID())
                .task(Task.builder().id(taskId).build())
                .uploadedBy(User.builder().id(UUID.randomUUID()).lastName("Иванов").firstName("Иван").build())
                .originalName("a.txt")
                .type(ArtifactType.FILE)
                .uploadedAt(LocalDateTime.now())
                .build();
        when(taskAttachmentService.getTaskArtifacts(taskId)).thenReturn(List.of(attachment));

        var response = controller.getAttachments(taskId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("a.txt", response.getBody().get(0).getOriginalName());
    }

    @Test
    void deleteShouldReturnNoContent() {
        UUID attachmentId = UUID.randomUUID();
        var response = controller.delete(attachmentId);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(taskAttachmentService).deleteAttachment(attachmentId);
    }

    @Test
    void getDownloadUrlShouldReturnStringBody() {
        UUID attachmentId = UUID.randomUUID();
        when(taskAttachmentService.getDownloadUrl(attachmentId)).thenReturn("url");
        var response = controller.getDownloadUrl(attachmentId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("url", response.getBody());
    }

    @Test
    void uploadAndAddLinkShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        org.springframework.web.multipart.MultipartFile file = org.mockito.Mockito.mock(org.springframework.web.multipart.MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        TaskAttachment createdFile = TaskAttachment.builder()
                .id(UUID.randomUUID())
                .task(Task.builder().id(taskId).build())
                .uploadedBy(User.builder().id(UUID.randomUUID()).lastName("A").firstName("B").build())
                .originalName("f.txt")
                .uploadedAt(LocalDateTime.now())
                .type(ArtifactType.FILE)
                .build();
        when(taskAttachmentService.addAttachment(taskId, file)).thenReturn(createdFile);

        var uploadResponse = controller.upload(taskId, java.util.Arrays.asList(file, null));
        assertEquals(HttpStatus.OK, uploadResponse.getStatusCode());
        assertEquals(1, uploadResponse.getBody().size());

        TaskAttachment link = TaskAttachment.builder()
                .id(UUID.randomUUID())
                .task(Task.builder().id(taskId).build())
                .uploadedBy(User.builder().id(UUID.randomUUID()).lastName("A").firstName("B").build())
                .originalName("L")
                .type(ArtifactType.LINK)
                .uploadedAt(LocalDateTime.now())
                .build();
        when(taskAttachmentService.addLink(taskId, "https://x", "L")).thenReturn(link);
        var linkResponse = controller.addLink(taskId, "https://x", "L");
        assertEquals(HttpStatus.OK, linkResponse.getStatusCode());
    }
}

