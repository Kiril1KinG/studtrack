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
public class ProjectCreateRequest {

    @NotBlank(message = "Название проекта обязательно")
    @Size(max = 100, message = "Название проекта не должно превышать 100 символов")
    private String name;

    @Size(max = 500, message = "Описание проекта не должно превышать 500 символов")
    private String description;
}