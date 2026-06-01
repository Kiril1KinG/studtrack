package ru.diploma.studtrack.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.diploma.studtrack.model.TaskReviewer;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * DTO ответа с данными ревьюера.
 */
public class TaskReviewerResponse {

    private UUID id;
    private UserResponse user;
    private TaskReviewer.ReviewStatus status;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}