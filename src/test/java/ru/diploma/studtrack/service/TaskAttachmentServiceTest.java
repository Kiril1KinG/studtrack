package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.mock.web.MockMultipartFile;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.model.ArtifactType;
import ru.diploma.studtrack.model.Comment;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskAttachmentRepository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskAttachmentServiceTest {

    @Mock private TaskAttachmentRepository repository;
    @Mock private TaskService taskService;
    @Mock private UserService userService;
    @Mock private ProjectService projectService;
    @Mock private MinioStorageService minioStorageService;

    private TaskAttachmentService service;

    @BeforeEach
    void setUp() {
        service = new TaskAttachmentService(repository, taskService, userService, projectService, minioStorageService);
    }

    @Test
    void addAttachmentShouldUploadAndSave() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        User actor = User.builder().id(UUID.randomUUID()).build();
        MockMultipartFile file = new MockMultipartFile("file", "report.xlsx", "application/vnd.ms-excel", "abc".getBytes());
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(repository.save(any(TaskAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskAttachment saved = service.addAttachment(taskId, file);
        assertEquals(ArtifactType.FILE, saved.getType());
        verify(minioStorageService).upload(any(String.class), any(), eq(file.getContentType()), eq(file.getSize()));
    }

    @Test
    void addLinkShouldNormalizeAndPersist() {
        UUID taskId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
        User actor = User.builder().id(UUID.randomUUID()).build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(userService.getCurrentUser()).thenReturn(actor);
        when(repository.save(any(TaskAttachment.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskAttachment link = service.addLink(taskId, "https://example.com/a", "Docs");
        assertEquals(ArtifactType.LINK, link.getType());
        assertEquals("Docs", link.getOriginalName());
    }

    @Test
    void attachToCommentShouldRejectForeignUploader() {
        UUID taskId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        Task task = Task.builder().id(taskId).build();
        TaskAttachment attachment = TaskAttachment.builder()
                .id(attachmentId)
                .task(task)
                .type(ArtifactType.FILE)
                .uploadedBy(User.builder().id(UUID.randomUUID()).build())
                .build();
        when(repository.findByIdIn(List.of(attachmentId))).thenReturn(List.of(attachment));
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());

        assertThrows(AccessDeniedException.class,
                () -> service.attachToComment(taskId, Comment.builder().attachments(new ArrayList<>()).build(), List.of(attachmentId)));
    }

    @Test
    void attachToCommentShouldSetCommentAndSave() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        TaskAttachment attachment = TaskAttachment.builder()
                .id(attachmentId)
                .task(Task.builder().id(taskId).build())
                .type(ArtifactType.FILE)
                .uploadedBy(User.builder().id(userId).build())
                .build();
        Comment comment = Comment.builder().attachments(new ArrayList<>()).build();
        when(repository.findByIdIn(List.of(attachmentId))).thenReturn(List.of(attachment));
        when(userService.getCurrentUserId()).thenReturn(userId);
        when(repository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<TaskAttachment> result = service.attachToComment(taskId, comment, List.of(attachmentId));
        assertEquals(1, result.size());
        assertEquals(comment, result.get(0).getComment());
    }

    @Test
    void deleteAttachmentShouldRejectNonOwnerNonAuthor() {
        UUID id = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Project project = Project.builder().id(UUID.randomUUID()).build();
        Task task = Task.builder().project(project).build();
        TaskAttachment attachment = TaskAttachment.builder()
                .id(id)
                .task(task)
                .uploadedBy(User.builder().id(UUID.randomUUID()).build())
                .type(ArtifactType.FILE)
                .storedKey("k")
                .build();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(attachment));
        when(userService.getCurrentUserId()).thenReturn(currentUserId);
        when(projectService.isOwner(project.getId(), currentUserId)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.deleteAttachment(id));
    }

    @Test
    void deleteCommentAttachmentsShouldDeleteSelected() {
        UUID removeId = UUID.randomUUID();
        TaskAttachment keep = TaskAttachment.builder().id(UUID.randomUUID()).type(ArtifactType.FILE).storedKey("keep").build();
        TaskAttachment remove = TaskAttachment.builder().id(removeId).type(ArtifactType.FILE).storedKey("remove").build();
        Comment comment = Comment.builder().attachments(new ArrayList<>(List.of(keep, remove))).build();

        service.deleteCommentAttachments(comment, List.of(removeId));
        assertEquals(1, comment.getAttachments().size());
        verify(minioStorageService).delete("remove");
        verify(repository).delete(remove);
    }

    @Test
    void getDownloadUrlShouldUsePresignedService() {
        UUID id = UUID.randomUUID();
        TaskAttachment attachment = TaskAttachment.builder()
                .id(id)
                .storedKey("k")
                .task(Task.builder().project(Project.builder().id(UUID.randomUUID()).build()).build())
                .build();
        when(repository.findById(id)).thenReturn(java.util.Optional.of(attachment));
        when(minioStorageService.getPresignedDownloadUrl("k", Duration.ofMinutes(15))).thenReturn("url");

        assertEquals("url", service.getDownloadUrl(id));
    }

    @Test
    void getProjectArtifactsShouldSupportSortKeys() {
        UUID projectId = UUID.randomUUID();
        when(repository.findByTaskProjectIdAndCommentIsNull(eq(projectId), any(Sort.class))).thenReturn(List.of());
        assertEquals(List.of(), service.getProjectArtifacts(projectId, "task"));
        assertEquals(List.of(), service.getProjectArtifacts(projectId, "type"));
        assertEquals(List.of(), service.getProjectArtifacts(projectId, "oldest"));
        assertEquals(List.of(), service.getProjectArtifacts(projectId, "newest"));
        assertEquals(List.of(), service.getProjectArtifacts(projectId, "unknown"));
    }

    @Test
    void attachToCommentShouldRejectLinkType() {
        UUID taskId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        TaskAttachment attachment = TaskAttachment.builder()
                .id(attachmentId)
                .task(Task.builder().id(taskId).build())
                .type(ArtifactType.LINK)
                .uploadedBy(User.builder().id(userId).build())
                .build();
        when(repository.findByIdIn(List.of(attachmentId))).thenReturn(List.of(attachment));
        when(userService.getCurrentUserId()).thenReturn(userId);
        Comment comment = Comment.builder().attachments(new ArrayList<>()).build();

        assertThrows(InvalidStateException.class, () -> service.attachToComment(taskId, comment, List.of(attachmentId)));
    }
}

