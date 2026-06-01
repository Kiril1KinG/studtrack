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
 * DTO ответа с данными комментария.
 */
public class CommentResponse {

    private UUID id;
    private UserResponse author;
    private String content;
    private UUID roundId;
    private LocalDateTime createdAt;
}