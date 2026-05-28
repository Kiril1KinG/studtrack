package ru.diploma.studtrack.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFoundShouldReturn404Body() {
        ResponseEntity<Object> response = handler.handleNotFound(new NotFoundException("Task", 1));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(404, body.get("status"));
        assertEquals("NOT_FOUND", body.get("errorCode"));
    }

    @Test
    void handleAccessDeniedShouldReturn403Body() {
        ResponseEntity<Object> response = handler.handleAccessDenied(new AccessDeniedException("denied"));
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(403, body.get("status"));
        assertEquals("ACCESS_DENIED", body.get("errorCode"));
    }

    @Test
    void handleAlreadyExistsShouldReturn409Body() {
        ResponseEntity<Object> response = handler.handleAlreadyExists(new AlreadyExistsException("exists"));
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(409, body.get("status"));
        assertEquals("ALREADY_EXISTS", body.get("errorCode"));
    }

    @Test
    void handleInvalidStateShouldReturn400Body() {
        ResponseEntity<Object> response = handler.handleInvalidState(new InvalidStateException("bad"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(400, body.get("status"));
        assertEquals("INVALID_STATE", body.get("errorCode"));
    }

    @Test
    void handleIllegalArgumentShouldReturn400Body() {
        ResponseEntity<Object> response = handler.handleIllegalArgument(new IllegalArgumentException("oops"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(400, body.get("status"));
        assertEquals("BAD_REQUEST", body.get("errorCode"));
        assertEquals("oops", body.get("message"));
    }

    @Test
    void handleGenericShouldReturn500Body() {
        ResponseEntity<Object> response = handler.handleGeneric(new RuntimeException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals(500, body.get("status"));
        assertEquals("INTERNAL_ERROR", body.get("errorCode"));
    }
}
