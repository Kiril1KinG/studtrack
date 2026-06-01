package ru.diploma.studtrack.exception;

/**
 * Сигнализирует об отсутствии запрошенного ресурса.
 */
public class NotFoundException extends StudTrackException {

    /**
     * Создаёт исключение «ресурс не найден» по имени и идентификатору.
     *
     * @param resourceName имя ресурса
     * @param identifier идентификатор ресурса
     */
    public NotFoundException(String resourceName, Object identifier) {
        super(
                String.format("%s с идентификатором '%s' не найден", resourceName, identifier),
                "NOT_FOUND"
        );
    }

    /**
     * Создаёт исключение «ресурс не найден» с произвольным сообщением.
     *
     * @param message текст ошибки
     */
    public NotFoundException(String message) {
        super(message, "NOT_FOUND");
    }
}