package ru.diploma.studtrack.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * DTO ответа с данными пользователя.
 */
public class UserResponse {

    private UUID id;
    private String email;
    private String lastName;
    private String firstName;
    private String patronymic;
    private String fullName;
    private LocalDateTime createdAt;
}