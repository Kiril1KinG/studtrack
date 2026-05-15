package ru.diploma.studtrack.exception;

public class NotFoundException extends StudTrackException {

    public NotFoundException(String resourceName, Object identifier) {
        super(
                String.format("%s с идентификатором '%s' не найден", resourceName, identifier),
                "NOT_FOUND"
        );
    }

    public NotFoundException(String message) {
        super(message, "NOT_FOUND");
    }
}