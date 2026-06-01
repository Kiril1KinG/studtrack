package ru.diploma.studtrack.exception;

import lombok.Getter;

@Getter
/**
 * Базовый класс доменных исключений приложения с кодом ошибки.
 */
public abstract class StudTrackException extends RuntimeException {

    /**
     * Машиночитаемый код ошибки.
     */
    private final String errorCode;

    /**
     * Создаёт исключение с сообщением и кодом ошибки.
     *
     * @param message текст ошибки
     * @param errorCode код ошибки
     */
    protected StudTrackException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Создаёт исключение с сообщением, кодом и причиной.
     *
     * @param message текст ошибки
     * @param errorCode код ошибки
     * @param cause причина исключения
     */
    protected StudTrackException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}