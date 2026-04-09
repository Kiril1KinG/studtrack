package ru.diploma.studtrack.exception;

public class InvalidStateException extends StudTrackException {

    public InvalidStateException(String message) {
        super(message, "INVALID_STATE");
    }

    public InvalidStateException(String action, String currentState, String expectedState) {
        super(
                String.format("Невозможно выполнить '%s'. Текущее состояние: %s, ожидалось: %s",
                        action, currentState, expectedState),
                "INVALID_STATE"
        );
    }
}