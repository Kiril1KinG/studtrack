package ru.diploma.studtrack.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.NoHandlerFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebExceptionHandlerTest {

    private WebExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WebExceptionHandler();
    }

    @Test
    void handleNotFoundShouldReturn404Page() {
        Model model = new ExtendedModelMap();
        String view = handler.handleNotFound(new NotFoundException("missing"), model);
        assertEquals("error/404", view);
        assertEquals(404, model.getAttribute("statusCode"));
        assertEquals("Страница не найдена", model.getAttribute("pageTitle"));
    }

    @Test
    void handleAccessDeniedShouldReturn403Page() {
        Model model = new ExtendedModelMap();
        String view = handler.handleAccessDenied(new AccessDeniedException("denied"), model);
        assertEquals("error/403", view);
        assertEquals(403, model.getAttribute("statusCode"));
        assertEquals("Доступ запрещен", model.getAttribute("pageTitle"));
    }

    @Test
    void handleNoHandlerShouldReturn404Page() {
        Model model = new ExtendedModelMap();
        NoHandlerFoundException ex = new NoHandlerFoundException("GET", "/x", null);
        String view = handler.handleNoHandler(ex, model);
        assertEquals("error/404", view);
        assertEquals(404, model.getAttribute("statusCode"));
    }

    @Test
    void handleGenericShouldReturn500Page() {
        Model model = new ExtendedModelMap();
        String view = handler.handleGeneric(new RuntimeException("boom"), model);
        assertEquals("error/500", view);
        assertEquals(500, model.getAttribute("statusCode"));
        assertEquals("Внутренняя ошибка", model.getAttribute("pageTitle"));
    }
}
