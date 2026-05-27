package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskHistoryRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskHistoryService {

    private final TaskHistoryRepository taskHistoryRepository;

    public List<TaskHistory> getByTask(UUID taskId) {
        return taskHistoryRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    public String toHumanMessage(TaskHistory entry) {
        return switch (entry.getEventType()) {
            case TASK_FIELD_CHANGED -> {
                if ("attachments".equals(entry.getFieldName())) {
                    yield attachmentChangeMessage(entry.getOldValue(), entry.getNewValue());
                }
                yield "изменил(а) " + fieldLabel(entry.getFieldName()) + ": "
                        + valueLabel(entry.getFieldName(), entry.getOldValue()) + " -> "
                        + valueLabel(entry.getFieldName(), entry.getNewValue());
            }
            case TASK_STATUS_CHANGED -> statusChangeMessage(entry.getDetailsJson());
            case ASSIGNEE_ADDED -> "назначил(а) исполнителя " + safeName(detailValue(entry, "assigneeName"));
            case ASSIGNEE_REMOVED -> "снял(а) исполнителя " + safeName(detailValue(entry, "assigneeName"));
            case REVIEWER_ADDED -> "добавил(а) ревьюера " + safeName(detailValue(entry, "reviewerName"));
            case REVIEWER_REMOVED -> "удалил(а) ревьюера " + safeName(detailValue(entry, "reviewerName"));
            case REVIEW_SUBMITTED -> "отправил(а) ревью: " + mapReviewStatus(detailValue(entry, "status"));
            case REVIEW_ROUND_CREATED -> "создал(а) раунд ревью #" + safeNumber(detailValue(entry, "roundNumber"));
            case REVIEW_ROUND_COMPLETED -> "завершил(а) раунд ревью #" + safeNumber(detailValue(entry, "roundNumber"));
            case REVIEW_ROUND_CANCELED -> "отменил(а) раунд ревью #" + safeNumber(detailValue(entry, "roundNumber"));
        };
    }

    @Transactional
    public void recordFieldChange(Task task,
                                  User actor,
                                  String fieldName,
                                  Object oldValue,
                                  Object newValue) {
        String oldStr = stringify(oldValue);
        String newStr = stringify(newValue);
        if (Objects.equals(oldStr, newStr)) {
            return;
        }

        TaskHistory entry = TaskHistory.builder()
                .task(task)
                .actor(actor)
                .eventType(TaskHistory.EventType.TASK_FIELD_CHANGED)
                .fieldName(fieldName)
                .oldValue(oldStr)
                .newValue(newStr)
                .build();
        taskHistoryRepository.save(entry);
    }

    @Transactional
    public void recordEvent(Task task,
                            User actor,
                            TaskHistory.EventType eventType) {
        recordEvent(task, actor, eventType, (String) null);
    }

    @Transactional
    public void recordEvent(Task task,
                            User actor,
                            TaskHistory.EventType eventType,
                            Map<String, Object> details) {
        recordEvent(task, actor, eventType, toJson(details));
    }

    @Transactional
    public void recordEvent(Task task,
                            User actor,
                            TaskHistory.EventType eventType,
                            String detailsJson) {
        TaskHistory entry = TaskHistory.builder()
                .task(task)
                .actor(actor)
                .eventType(eventType)
                .detailsJson(detailsJson)
                .build();
        taskHistoryRepository.save(entry);
    }

    private String stringify(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String fieldLabel(String fieldName) {
        if (fieldName == null) return "поле";
        return switch (fieldName) {
            case "title" -> "название";
            case "description" -> "описание";
            case "priority" -> "приоритет";
            case "reviewRequired" -> "требуется ревью";
            case "deadline" -> "дедлайн";
            case "status" -> "статус";
            case "attachments" -> "вложение к задаче";
            default -> fieldName;
        };
    }

    private String valueLabel(String fieldName, String value) {
        if (value == null || value.isBlank()) {
            return "не указано";
        }
        if ("reviewRequired".equals(fieldName)) {
            return "true".equalsIgnoreCase(value) ? "Да" : "Нет";
        }
        if ("status".equals(fieldName)) {
            return mapTaskStatus(value);
        }
        if ("priority".equals(fieldName)) {
            return mapPriority(value);
        }
        return value;
    }

    private String mapTaskStatus(String value) {
        return switch (value) {
            case "BACKLOG" -> "Бэклог";
            case "TODO" -> "К выполнению";
            case "IN_PROGRESS" -> "В работе";
            case "REVIEW" -> "На ревью";
            case "DONE" -> "Готово";
            default -> value;
        };
    }

    private String mapPriority(String value) {
        return switch (value) {
            case "LOW" -> "Низкий";
            case "MEDIUM" -> "Средний";
            case "HIGH" -> "Высокий";
            default -> value;
        };
    }

    private String mapReviewStatus(String value) {
        if (value == null || value.isBlank()) return "—";
        return switch (value) {
            case "APPROVED" -> "одобрено";
            case "REJECTED" -> "требуются правки";
            case "PENDING" -> "ожидает проверки";
            default -> value;
        };
    }

    private String safeName(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private boolean isLinkHistoryValue(String value) {
        return value != null && value.startsWith("LINK::");
    }

    private String stripHistoryPrefix(String value) {
        if (value == null) {
            return null;
        }
        if (value.startsWith("FILE::")) {
            return value.substring("FILE::".length());
        }
        if (value.startsWith("LINK::")) {
            return value.substring("LINK::".length());
        }
        return value;
    }

    private String safeNumber(String value) {
        return value == null || value.isBlank() ? "?" : value;
    }

    private String attachmentChangeMessage(String oldVal, String newVal) {
        if ((newVal == null || newVal.isBlank()) && oldVal != null && !oldVal.isBlank()) {
            return isLinkHistoryValue(oldVal)
                    ? "удалил(а) ссылку из задачи: " + safeName(stripHistoryPrefix(oldVal))
                    : "удалил(а) файл из задачи: " + safeName(stripHistoryPrefix(oldVal));
        }
        if (newVal != null && !newVal.isBlank()) {
            return isLinkHistoryValue(newVal)
                    ? "добавил(а) ссылку к задаче: " + safeName(stripHistoryPrefix(newVal))
                    : "прикрепил(а) файл к задаче: " + safeName(stripHistoryPrefix(newVal));
        }
        return "изменил(а) вложения задачи";
    }

    private String statusChangeMessage(String detailsJson) {
        String oldStatusRaw = extractValue(detailsJson, "oldStatus");
        String newStatusRaw = extractValue(detailsJson, "newStatus");
        String newStatus = mapTaskStatus(newStatusRaw);
        if (oldStatusRaw == null || oldStatusRaw.isBlank()) {
            return "изменил(а) статус задачи на " + newStatus;
        }
        String oldStatus = mapTaskStatus(oldStatusRaw);
        return "изменил(а) статус задачи: " + oldStatus + " -> " + newStatus;
    }

    private String detailValue(TaskHistory entry, String key) {
        return extractValue(entry.getDetailsJson(), key);
    }

    private String extractValue(String json, String key) {
        if (json == null || json.isBlank() || key == null || key.isBlank()) {
            return null;
        }
        String token = "\"" + key + "\":";
        int start = json.indexOf(token);
        if (start < 0) {
            return null;
        }
        int valueStart = start + token.length();
        if (valueStart >= json.length()) {
            return null;
        }
        char first = json.charAt(valueStart);
        if (first == '"') {
            int end = json.indexOf('"', valueStart + 1);
            if (end < 0) return null;
            return json.substring(valueStart + 1, end)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\");
        }
        int end = json.indexOf(',', valueStart);
        if (end < 0) {
            end = json.indexOf('}', valueStart);
        }
        if (end < 0) {
            end = json.length();
        }
        String raw = json.substring(valueStart, end).trim();
        if ("null".equals(raw)) {
            return null;
        }
        return raw;
    }

    private String toJson(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            String key = escapeJson(entry.getKey());
            Object value = entry.getValue();
            if (value == null) {
                joiner.add("\"" + key + "\":null");
            } else if (value instanceof Number || value instanceof Boolean) {
                joiner.add("\"" + key + "\":" + value);
            } else {
                joiner.add("\"" + key + "\":\"" + escapeJson(String.valueOf(value)) + "\"");
            }
        }
        return joiner.toString();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
