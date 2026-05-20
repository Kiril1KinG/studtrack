package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.Comment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {

    @EntityGraph(attributePaths = {"author", "attachments", "attachments.uploadedBy"})
    List<Comment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    @EntityGraph(attributePaths = {"author", "attachments", "attachments.uploadedBy"})
    List<Comment> findByChangeRequestIdOrderByCreatedAtAsc(UUID changeRequestId);

    @EntityGraph(attributePaths = {"author", "attachments", "attachments.uploadedBy"})
    Optional<Comment> findDetailedById(UUID id);

    void deleteByTaskId(UUID taskId);
}