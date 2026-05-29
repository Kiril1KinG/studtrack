package ru.diploma.studtrack.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * DTO ответа с данными раунда ревью.
 */
public class TaskReviewRoundResponse {

    private UUID id;
    private Integer roundNumber;
    private UserResponse initiator;
    private String summaryComment;
    private LocalDateTime createdAt;
    private List<ChangeRequestResponse> changeRequests;
    private List<CommentResponse> comments;
}