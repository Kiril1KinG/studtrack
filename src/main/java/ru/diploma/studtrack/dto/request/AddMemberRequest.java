package ru.diploma.studtrack.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * DTO запроса добавления участника в проект.
 */
public class AddMemberRequest {

    private UUID userId;

    @Email(message = "Некорректный формат email")
    private String email;

    @AssertTrue(message = "Необходимо указать userId или email")
    public boolean isUserIdOrEmailPresent() {
        return userId != null || (email != null && !email.isBlank());
    }
}