package ru.diploma.studtrack.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice(basePackages = "ru.diploma.studtrack.controller.api")
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Object> handleNotFound(NotFoundException ex) {
        log.warn("Ресурс не найден: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Доступ запрещён: {}", ex.getMessage());
        return buildResponse(HttpStatus.FORBIDDEN, ex);
    }

    @ExceptionHandler(AlreadyExistsException.class)
    public ResponseEntity<Object> handleAlreadyExists(AlreadyExistsException ex) {
        log.warn("Конфликт данных: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex);
    }

    @ExceptionHandler(InvalidStateException.class)
    public ResponseEntity<Object> handleInvalidState(InvalidStateException ex) {
        log.warn("Некорректное состояние: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Некорректный аргумент: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Некорректный запрос");
        body.put("message", ex.getMessage());
        body.put("errorCode", "BAD_REQUEST");
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneric(Exception ex) {
        log.error("Внутренняя ошибка сервера", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Внутренняя ошибка сервера");
        body.put("message", "Произошла непредвиденная ошибка. Пожалуйста, попробуйте позже.");
        body.put("errorCode", "INTERNAL_ERROR");
        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<Object> buildResponse(HttpStatus status, StudTrackException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", getErrorDescription(status));
        body.put("message", ex.getMessage());
        body.put("errorCode", ex.getErrorCode());
        return new ResponseEntity<>(body, status);
    }

    private String getErrorDescription(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> "Ресурс не найден";
            case FORBIDDEN -> "Доступ запрещён";
            case CONFLICT -> "Конфликт данных";
            case BAD_REQUEST -> "Некорректный запрос";
            default -> "Ошибка";
        };
    }
}