package ru.diploma.studtrack.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.diploma.studtrack.model.TaskReviewer;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSubmitRequest {

    @NotNull(message = "Статус ревью обязателен")
    private TaskReviewer.ReviewStatus status;

    private String comment;
}