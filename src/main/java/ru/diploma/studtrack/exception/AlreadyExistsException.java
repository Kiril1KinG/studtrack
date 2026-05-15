package ru.diploma.studtrack.exception;

public class AlreadyExistsException extends StudTrackException {

    public AlreadyExistsException(String resourceName, String field, Object value) {
        super(
                String.format("%s с %s '%s' уже существует", resourceName, field, value),
                "ALREADY_EXISTS"
        );
    }

    public AlreadyExistsException(String message) {
        super(message, "ALREADY_EXISTS");
    }
}