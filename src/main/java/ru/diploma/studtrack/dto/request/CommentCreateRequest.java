package ru.diploma.studtrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentCreateRequest {

    @NotBlank(message = "Текст комментария обязателен")
    @Size(max = 2000, message = "Текст комментария не должен превышать 2000 символов")
    private String content;

    private List<UUID> attachmentIds;
}