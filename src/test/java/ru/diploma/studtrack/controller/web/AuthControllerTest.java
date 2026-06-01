package ru.diploma.studtrack.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import ru.diploma.studtrack.exception.AlreadyExistsException;
import ru.diploma.studtrack.service.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private UserService userService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(userService);
    }

    @Test
    void loginPageShouldExposeErrorAndLogoutFlags() {
        Model model = new ExtendedModelMap();
        String view = controller.loginPage("1", "1", model);
        assertEquals("auth/login", view);
        assertEquals("Неверный email или пароль", model.getAttribute("error"));
        assertEquals("Вы успешно вышли из системы", model.getAttribute("message"));
    }

    @Test
    void registerShouldRejectPasswordMismatch() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.register("a@b.c", "123", "456", "Иванов", "Иван", null, redirect);
        assertEquals("redirect:/auth/register", view);
        assertEquals("Пароли не совпадают", redirect.getFlashAttributes().get("error"));
    }

    @Test
    void registerShouldSetFlashErrorWhenServiceThrows() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        doThrow(new IllegalArgumentException("weak")).when(userService)
                .register(anyString(), anyString(), anyString(), anyString(), isNull());

        String view = controller.register("a@b.c", "abc12345", "abc12345", "Иванов", "Иван", null, redirect);
        assertEquals("redirect:/auth/register", view);
        assertEquals("weak", redirect.getFlashAttributes().get("error"));
    }

    @Test
    void registerShouldSetFlashErrorWhenUserAlreadyExists() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        doThrow(new AlreadyExistsException("Пользователь с email 'a@b.c' уже существует")).when(userService)
                .register(anyString(), anyString(), anyString(), anyString(), isNull());

        String view = controller.register("a@b.c", "abc12345", "abc12345", "Иванов", "Иван", null, redirect);
        assertEquals("redirect:/auth/register", view);
        assertEquals("Пользователь с email 'a@b.c' уже существует", redirect.getFlashAttributes().get("error"));
    }

    @Test
    void registerShouldRedirectToLoginOnSuccess() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        String view = controller.register("a@b.c", "abc12345", "abc12345", "Иванов", "Иван", null, redirect);
        assertEquals("redirect:/auth/login?registered", view);
        verify(userService).register("a@b.c", "abc12345", "Иванов", "Иван", null);
    }
}

