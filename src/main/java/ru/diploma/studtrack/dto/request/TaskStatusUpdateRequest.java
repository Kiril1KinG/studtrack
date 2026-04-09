package ru.diploma.studtrack.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.diploma.studtrack.model.Task;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusUpdateRequest {

    @NotNull(message = "Статус задачи обязателен")
    private Task.TaskStatus status;
}