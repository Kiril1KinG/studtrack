package ru.diploma.studtrack.exception;

/**
 * Сигнализирует о невозможности выполнить действие в текущем состоянии.
 */
public class InvalidStateException extends StudTrackException {

    /**
     * Создаёт исключение некорректного состояния с сообщением.
     *
     * @param message текст ошибки
     */
    public InvalidStateException(String message) {
        super(message, "INVALID_STATE");
    }

    /**
     * Создаёт исключение некорректного состояния с деталями контекста.
     *
     * @param action действие
     * @param currentState текущее состояние
     * @param expectedState ожидаемое состояние
     */
    public InvalidStateException(String action, String currentState, String expectedState) {
        super(
                String.format("Невозможно выполнить '%s'. Текущее состояние: %s, ожидалось: %s",
                        action, currentState, expectedState),
                "INVALID_STATE"
        );
    }
}