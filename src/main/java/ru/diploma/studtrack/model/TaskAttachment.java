package ru.diploma.studtrack.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "task_attachments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAttachment {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.RANDOM)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type", nullable = false, length = 20)
    @Builder.Default
    private ArtifactType type = ArtifactType.FILE;

    @Column(name = "original_name", nullable = false, length = 300)
    private String originalName;

    @Column(name = "stored_key", nullable = false, unique = true, length = 500)
    private String storedKey;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long size;

    @Column(name = "link_url", length = 2000)
    private String linkUrl;

    @Column(name = "link_title", length = 300)
    private String linkTitle;

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    public String getDisplayName() {
        if (type == ArtifactType.LINK) {
            if (linkTitle != null && !linkTitle.isBlank()) {
                return linkTitle;
            }
            return linkUrl;
        }
        return originalName;
    }

    public String getDisplayNameShort() {
        String value = getDisplayName();
        if (value == null || value.isBlank()) {
            return "file";
        }
        if (type == ArtifactType.FILE) {
            return shortenKeepingExtension(value, 22, 10);
        }
        return shortenMiddle(value, 34, 16);
    }

    private String shortenKeepingExtension(String value, int maxLen, int headLen) {
        int dot = value.lastIndexOf('.');
        if (dot <= 0 || dot == value.length() - 1 || value.length() <= maxLen) {
            return shortenMiddle(value, maxLen, headLen);
        }
        String ext = value.substring(dot + 1);
        if (ext.length() >= maxLen - 4) {
            return shortenMiddle(value, maxLen, headLen);
        }
        String head = value.substring(0, dot);
        int availableHead = maxLen - ext.length() - 4;
        if (availableHead < 4 || head.length() <= availableHead) {
            return value;
        }
        return head.substring(0, availableHead) + "..." + ext;
    }

    private String shortenMiddle(String value, int maxLen, int headLen) {
        if (value.length() <= maxLen) {
            return value;
        }
        int safeHeadLen = Math.max(3, Math.min(headLen, maxLen - 6));
        int tailLen = Math.max(2, maxLen - safeHeadLen - 3);
        return value.substring(0, safeHeadLen) + "..." + value.substring(value.length() - tailLen);
    }
}
