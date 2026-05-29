package ru.diploma.studtrack.controller.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.diploma.studtrack.service.UserService;

@Slf4j
/**
 * Обрабатывает веб-страницы аутентификации и регистрации пользователя.
 */
@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    /**
     * Предоставляет операции регистрации и управления пользователями.
     */
    private final UserService userService;

    /**
     * Отображает страницу входа и выводит сообщения о статусе авторизации.
     *
     * @param error признак ошибки входа
     * @param logout признак успешного выхода
     * @param model модель представления
     * @return имя шаблона страницы входа
     */
    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (error != null) {
            model.addAttribute("error", "Неверный email или пароль");
        }
        if (logout != null) {
            model.addAttribute("message", "Вы успешно вышли из системы");
        }
        model.addAttribute("pageTitle", "Вход");
        return "auth/login";
    }

    /**
     * Отображает форму регистрации.
     *
     * @param model модель представления
     * @return имя шаблона формы регистрации
     */
    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("pageTitle", "Регистрация");
        return "auth/register";
    }

    /**
     * Регистрирует нового пользователя и перенаправляет на страницу входа.
     *
     * @param email email пользователя
     * @param password пароль
     * @param confirmPassword подтверждение пароля
     * @param lastName фамилия
     * @param firstName имя
     * @param patronymic отчество
     * @param redirectAttributes атрибуты flash-сообщений
     * @return URL перенаправления после обработки регистрации
     */
    @PostMapping("/register")
    public String register(@RequestParam String email,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam String lastName,
                           @RequestParam String firstName,
                           @RequestParam(required = false) String patronymic,
                           RedirectAttributes redirectAttributes) {
        if (!password.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Пароли не совпадают");
            return "redirect:/auth/register";
        }
        log.info("Регистрация нового пользователя: {}", email);
        try {
            userService.register(email, password, lastName, firstName, patronymic);
            return "redirect:/auth/login?registered";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/auth/register";
        }
    }
}