package ru.diploma.studtrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.diploma.studtrack.model.Task;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * DTO запроса обновления задачи.
 */
public class TaskUpdateRequest {

    @NotBlank(message = "Название задачи обязательно")
    @Size(max = 200, message = "Название задачи не должно превышать 200 символов")
    private String title;

    @Size(max = 2000, message = "Описание задачи не должно превышать 2000 символов")
    private String description;

    @NotNull(message = "Приоритет задачи обязателен")
    private Task.Priority priority;

    private UUID assigneeId;

    private boolean reviewRequired;

    private LocalDate deadline;
}