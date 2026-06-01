package ru.diploma.studtrack.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**
 * DTO ответа с данными участника проекта.
 */
public class ProjectMemberResponse {

    private UUID id;
    private UserResponse user;
    private LocalDateTime joinedAt;
}