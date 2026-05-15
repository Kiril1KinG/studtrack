package ru.diploma.studtrack.exception;

import lombok.Getter;

@Getter
public abstract class StudTrackException extends RuntimeException {

    private final String errorCode;

    protected StudTrackException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    protected StudTrackException(String message, String errorCode, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}