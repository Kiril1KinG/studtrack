package ru.diploma.studtrack.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.diploma.studtrack.model.Task;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {

    private UUID id;
    private UUID projectId;
    private String title;
    private String description;
    private Task.TaskStatus status;
    private Task.Priority priority;
    private UserResponse assignee;
    private List<UserResponse> assignees;
    private boolean reviewRequired;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<TaskReviewerResponse> reviewers;
    private int commentCount;
}