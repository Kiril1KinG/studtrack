package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.TaskAttachment;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, UUID> {

    List<TaskAttachment> findByTaskIdOrderByUploadedAtDesc(UUID taskId);
    List<TaskAttachment> findByTaskIdAndCommentIsNullOrderByUploadedAtDesc(UUID taskId);

    List<TaskAttachment> findByIdIn(List<UUID> ids);
}
