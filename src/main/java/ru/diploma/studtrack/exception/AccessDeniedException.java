package ru.diploma.studtrack.exception;

/**
 * Сигнализирует об отказе в доступе к действию или ресурсу.
 */
public class AccessDeniedException extends StudTrackException {

    /**
     * Создаёт исключение доступа с произвольным сообщением.
     *
     * @param message текст ошибки
     */
    public AccessDeniedException(String message) {
        super(message, "ACCESS_DENIED");
    }

    /**
     * Создаёт исключение доступа по действию и причине.
     *
     * @param action действие
     * @param reason причина отказа
     */
    public AccessDeniedException(String action, String reason) {
        super(
                String.format("Нет прав на выполнение действия '%s': %s", action, reason),
                "ACCESS_DENIED"
        );
    }
}