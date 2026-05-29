package ru.diploma.studtrack.service;

import org.springframework.stereotype.Service;
import ru.diploma.studtrack.exception.StudTrackException;

@Service
/**
 * Возвращает человекочитаемые сообщения ошибок для веб-слоя.
 */
public class WebErrorMessageService {

    /**
     * Разрешает текст ошибки для отображения пользователю.
     *
     * @param exception исходное исключение
     * @param fallbackMessage сообщение по умолчанию
     * @return сообщение для UI
     */
    public String resolve(Exception exception, String fallbackMessage) {
        if (exception instanceof StudTrackException studTrackException) {
            String message = studTrackException.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        if (exception instanceof IllegalArgumentException illegalArgumentException) {
            String message = illegalArgumentException.getMessage();
            if (message != null && !message.isBlank()) {
                return message;
            }
        }
        return fallbackMessage;
    }
}
