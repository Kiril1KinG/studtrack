package ru.diploma.studtrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRequestCreateRequest {

    @NotBlank(message = "Текст замечания обязателен")
    @Size(max = 1000, message = "Текст замечания не должен превышать 1000 символов")
    private String content;
}