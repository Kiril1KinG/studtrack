package ru.diploma.studtrack.service;

import org.junit.jupiter.api.Test;
import ru.diploma.studtrack.exception.NotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebErrorMessageServiceTest {

    private final WebErrorMessageService service = new WebErrorMessageService();

    @Test
    void resolveShouldReturnStudTrackExceptionMessage() {
        String result = service.resolve(new NotFoundException("Не найдено"), "fallback");
        assertEquals("Не найдено", result);
    }

    @Test
    void resolveShouldReturnIllegalArgumentMessage() {
        String result = service.resolve(new IllegalArgumentException("bad input"), "fallback");
        assertEquals("bad input", result);
    }

    @Test
    void resolveShouldFallbackForEmptyMessages() {
        String result = service.resolve(new IllegalArgumentException(" "), "fallback");
        assertEquals("fallback", result);
    }

    @Test
    void resolveShouldFallbackForUnknownException() {
        String result = service.resolve(new RuntimeException("boom"), "fallback");
        assertEquals("fallback", result);
    }
}

