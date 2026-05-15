package ru.diploma.studtrack.exception;

public class AccessDeniedException extends StudTrackException {

    public AccessDeniedException(String message) {
        super(message, "ACCESS_DENIED");
    }

    public AccessDeniedException(String action, String reason) {
        super(
                String.format("Нет прав на выполнение действия '%s': %s", action, reason),
                "ACCESS_DENIED"
        );
    }
}