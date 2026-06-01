package ru.diploma.studtrack.exception;

/**
 * Сигнализирует о попытке создать уже существующую сущность.
 */
public class AlreadyExistsException extends StudTrackException {

    /**
     * Создаёт исключение конфликта по имени ресурса и уникальному полю.
     *
     * @param resourceName имя ресурса
     * @param field имя поля
     * @param value значение поля
     */
    public AlreadyExistsException(String resourceName, String field, Object value) {
        super(
                String.format("%s с %s '%s' уже существует", resourceName, field, value),
                "ALREADY_EXISTS"
        );
    }

    /**
     * Создаёт исключение конфликта с произвольным сообщением.
     *
     * @param message текст ошибки
     */
    public AlreadyExistsException(String message) {
        super(message, "ALREADY_EXISTS");
    }
}