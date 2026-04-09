package ru.diploma.studtrack.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentUpdateRequest {

    @NotBlank(message = "Текст комментария обязателен")
    @Size(max = 2000, message = "Текст комментария не должен превышать 2000 символов")
    private String content;
}