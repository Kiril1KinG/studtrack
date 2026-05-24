package ru.diploma.studtrack.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.diploma.studtrack.model.ArtifactType;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttachmentResponse {
    private UUID id;
    private UUID taskId;
    private UUID uploadedById;
    private String uploadedByName;
    private String originalName;
    private String contentType;
    private long size;
    private ArtifactType type;
    private String linkUrl;
    private String linkTitle;
    private LocalDateTime uploadedAt;
}
