package ru.diploma.studtrack.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAttachment;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.AttachmentHistoryValueService;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskAttachmentService;
import ru.diploma.studtrack.service.TaskHistoryService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.UserService;
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebAttachmentControllerTest {

    @Mock
    private TaskAttachmentService taskAttachmentService;
    @Mock
    private UserService userService;
    @Mock
    private TaskService taskService;
    @Mock
    private ProjectService projectService;
    @Mock
    private TaskHistoryService taskHistoryService;
    @Mock
    private AttachmentHistoryValueService attachmentHistoryValueService;
    @Mock
    private WebErrorMessageService webErrorMessageService;
    @Mock
    private MultipartFile file;

    private WebAttachmentController controller;
    private UUID taskId;
    private Task task;

    @BeforeEach
    void setUp() {
        controller = new WebAttachmentController(
                taskAttachmentService,
                userService,
                taskService,
                projectService,
                taskHistoryService,
                attachmentHistoryValueService,
                webErrorMessageService
        );
        taskId = UUID.randomUUID();
        task = Task.builder().id(taskId).project(Project.builder().id(UUID.randomUUID()).build()).build();
    }

    @Test
    void getAttachmentsShouldReturnFragment() {
        Model model = new ExtendedModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        when(taskAttachmentService.getTaskArtifacts(taskId)).thenReturn(List.of());
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(projectService.isOwner(any(), any())).thenReturn(true);

        String view = controller.getAttachments(taskId, model);

        assertEquals("fragments/task-attachments :: attachmentList", view);
        verify(projectService).checkMembership(task.getProject().getId());
    }

    @Test
    void addLinkShouldRecordHistoryAndReturnFragment() {
        Model model = new ExtendedModelMap();
        TaskAttachment created = TaskAttachment.builder().task(task).build();
        when(taskService.findById(taskId)).thenReturn(task);
        when(taskAttachmentService.addLink(taskId, "https://example.com", "t")).thenReturn(created);
        when(taskAttachmentService.getTaskArtifacts(taskId)).thenReturn(List.of(created));
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(projectService.isOwner(any(), any())).thenReturn(true);
        when(userService.getCurrentUser()).thenReturn(User.builder().id(UUID.randomUUID()).build());
        when(attachmentHistoryValueService.historyValueFor(created)).thenReturn("LINK::t");

        String view = controller.addLink(taskId, "https://example.com", "t", model);

        assertEquals("fragments/task-attachments :: attachmentList", view);
        verify(taskHistoryService).recordFieldChange(
                eq(task),
                any(User.class),
                eq("attachments"),
                isNull(),
                eq("LINK::t")
        );
    }

    @Test
    void uploadAttachmentShouldSetErrorMessageOnFailure() {
        Model model = new ExtendedModelMap();
        when(taskService.findById(taskId)).thenReturn(task);
        when(file.isEmpty()).thenReturn(false);
        when(taskAttachmentService.addAttachment(taskId, file)).thenThrow(new IllegalArgumentException("bad"));
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");
        when(taskAttachmentService.getTaskArtifacts(taskId)).thenReturn(List.of());
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(projectService.isOwner(any(), any())).thenReturn(true);

        String view = controller.uploadAttachment(taskId, List.of(file), model);

        assertEquals("fragments/task-attachments :: attachmentList", view);
        assertEquals("friendly", model.getAttribute("attachmentErrorMessage"));
    }
}

