package ru.diploma.studtrack.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.NoHandlerFoundException;

@Slf4j
@ControllerAdvice(annotations = Controller.class, basePackages = "ru.diploma.studtrack.controller.web")
public class WebExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public String handleNotFound(NotFoundException ex, Model model) {
        log.warn("Web 404: {}", ex.getMessage());
        return errorPage(model, HttpStatus.NOT_FOUND, "Страница не найдена", ex.getMessage(), "error/404");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public String handleAccessDenied(AccessDeniedException ex, Model model) {
        log.warn("Web 403: {}", ex.getMessage());
        return errorPage(model, HttpStatus.FORBIDDEN, "Доступ запрещен", ex.getMessage(), "error/403");
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public String handleNoHandler(NoHandlerFoundException ex, Model model) {
        String message = "Запрошенная страница не существует";
        log.warn("Web 404 no-handler: {} {}", ex.getHttpMethod(), ex.getRequestURL());
        return errorPage(model, HttpStatus.NOT_FOUND, "Страница не найдена", message, "error/404");
    }

    @ExceptionHandler(Exception.class)
    public String handleGeneric(Exception ex, Model model) {
        log.error("Web 500", ex);
        String message = "Произошла непредвиденная ошибка. Попробуйте обновить страницу или зайти позже.";
        return errorPage(model, HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка", message, "error/500");
    }

    private String errorPage(Model model,
                             HttpStatus status,
                             String title,
                             String message,
                             String viewName) {
        model.addAttribute("pageTitle", title);
        model.addAttribute("statusCode", status.value());
        model.addAttribute("statusText", status.getReasonPhrase());
        model.addAttribute("errorMessage", message);
        return viewName;
    }
}
