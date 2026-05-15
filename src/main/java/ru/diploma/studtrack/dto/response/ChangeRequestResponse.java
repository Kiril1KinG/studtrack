package ru.diploma.studtrack.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.diploma.studtrack.model.ChangeRequest;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeRequestResponse {

    private UUID id;
    private UUID roundId;
    private UserResponse author;
    private String content;
    private ChangeRequest.ChangeRequestStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}