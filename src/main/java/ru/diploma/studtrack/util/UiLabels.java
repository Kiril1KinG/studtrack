package ru.diploma.studtrack.util;

import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewer;

/**
 * Возвращает русскоязычные подписи для enum-значений UI.
 */
public final class UiLabels {

    private UiLabels() {
    }

    /**
     * Возвращает русское название статуса задачи.
     *
     * @param status статус задачи
     * @return подпись для интерфейса
     */
    public static String taskStatus(Task.TaskStatus status) {
        if (status == null) {
            return "Не указан";
        }
        return switch (status) {
            case BACKLOG -> "Бэклог";
            case TODO -> "К выполнению";
            case IN_PROGRESS -> "В работе";
            case REVIEW -> "На ревью";
            case DONE -> "Готово";
        };
    }

    /**
     * Возвращает русское название статуса ревьюера.
     *
     * @param status статус ревьюера
     * @return подпись для интерфейса
     */
    public static String reviewStatus(TaskReviewer.ReviewStatus status) {
        if (status == null) {
            return "Не указан";
        }
        return switch (status) {
            case PENDING -> "Ожидает";
            case APPROVED -> "Одобрено";
            case REJECTED -> "Отклонено";
        };
    }

    /**
     * Возвращает русское название приоритета задачи.
     *
     * @param priority приоритет задачи
     * @return подпись для интерфейса
     */
    public static String taskPriority(Task.Priority priority) {
        if (priority == null) {
            return "Не указан";
        }
        return switch (priority) {
            case LOW -> "Низкий";
            case MEDIUM -> "Средний";
            case HIGH -> "Высокий";
        };
    }
}
