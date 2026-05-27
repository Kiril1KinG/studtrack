package ru.diploma.studtrack.service;

import org.springframework.stereotype.Service;
import ru.diploma.studtrack.exception.StudTrackException;

@Service
public class WebErrorMessageService {

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
