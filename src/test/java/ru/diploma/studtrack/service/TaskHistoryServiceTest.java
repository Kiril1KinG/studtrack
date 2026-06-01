package ru.diploma.studtrack.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskHistoryRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskHistoryServiceTest {

    @Mock
    private TaskHistoryRepository repository;

    private TaskHistoryService service;

    @BeforeEach
    void setUp() {
        service = new TaskHistoryService(repository);
    }

    @Test
    void getByTaskShouldDelegateToRepository() {
        UUID taskId = UUID.randomUUID();
        List<TaskHistory> expected = List.of(TaskHistory.builder().build());
        when(repository.findByTaskIdOrderByCreatedAtDesc(taskId)).thenReturn(expected);

        assertEquals(expected, service.getByTask(taskId));
    }

    @Test
    void toHumanMessageShouldRenderAttachmentAddedAndRemoved() {
        TaskHistory added = TaskHistory.builder()
                .eventType(TaskHistory.EventType.TASK_FIELD_CHANGED)
                .fieldName("attachments")
                .newValue("FILE::spec.pdf")
                .build();
        TaskHistory removed = TaskHistory.builder()
                .eventType(TaskHistory.EventType.TASK_FIELD_CHANGED)
                .fieldName("attachments")
                .oldValue("LINK::https://example.com")
                .newValue(null)
                .build();

        assertEquals("прикрепил(а) файл к задаче: spec.pdf", service.toHumanMessage(added));
        assertEquals("удалил(а) ссылку из задачи: https://example.com", service.toHumanMessage(removed));
    }

    @Test
    void toHumanMessageShouldRenderStatusChangeFromDetailsJson() {
        TaskHistory statusChanged = TaskHistory.builder()
                .eventType(TaskHistory.EventType.TASK_STATUS_CHANGED)
                .detailsJson("{\"oldStatus\":\"TODO\",\"newStatus\":\"IN_PROGRESS\"}")
                .build();

        assertEquals("изменил(а) статус задачи: К выполнению -> В работе", service.toHumanMessage(statusChanged));
    }

    @Test
    void toHumanMessageShouldRenderReviewerAndAssigneeEvents() {
        TaskHistory assigneeAdded = TaskHistory.builder()
                .eventType(TaskHistory.EventType.ASSIGNEE_ADDED)
                .detailsJson("{\"assigneeName\":\"Иван\"}")
                .build();
        TaskHistory reviewerRemoved = TaskHistory.builder()
                .eventType(TaskHistory.EventType.REVIEWER_REMOVED)
                .detailsJson("{\"reviewerName\":\"Петр\"}")
                .build();

        assertEquals("назначил(а) исполнителя Иван", service.toHumanMessage(assigneeAdded));
        assertEquals("удалил(а) ревьюера Петр", service.toHumanMessage(reviewerRemoved));
    }

    @Test
    void recordFieldChangeShouldSkipWhenValuesSame() {
        service.recordFieldChange(Task.builder().build(), User.builder().build(), "title", "same", "same");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void recordFieldChangeShouldSaveWhenDifferent() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        User actor = User.builder().id(UUID.randomUUID()).build();
        service.recordFieldChange(task, actor, "priority", "LOW", "HIGH");

        verify(repository).save(argThat(entry ->
                entry.getTask().equals(task)
                        && entry.getActor().equals(actor)
                        && "priority".equals(entry.getFieldName())
                        && "LOW".equals(entry.getOldValue())
                        && "HIGH".equals(entry.getNewValue())));
    }

    @Test
    void recordEventShouldSaveJsonDetails() {
        Task task = Task.builder().id(UUID.randomUUID()).build();
        User actor = User.builder().id(UUID.randomUUID()).build();

        service.recordEvent(task, actor, TaskHistory.EventType.REVIEW_ROUND_CREATED, Map.of("roundNumber", 2, "comment", "ok"));

        verify(repository).save(argThat(entry ->
                entry.getEventType() == TaskHistory.EventType.REVIEW_ROUND_CREATED
                        && entry.getDetailsJson() != null
                        && entry.getDetailsJson().contains("\"roundNumber\":2")
                        && entry.getDetailsJson().contains("\"comment\":\"ok\"")));
    }

    @Test
    void toHumanMessageShouldHandleFieldMappingAndFallbacks() {
        TaskHistory fieldChange = TaskHistory.builder()
                .eventType(TaskHistory.EventType.TASK_FIELD_CHANGED)
                .fieldName("reviewRequired")
                .oldValue("false")
                .newValue("true")
                .build();
        String message = service.toHumanMessage(fieldChange);
        assertTrue(message.contains("требуется ревью"));
        assertTrue(message.contains("Нет"));
        assertTrue(message.contains("Да"));
    }
}

