package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.TaskReviewer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskReviewerRepository extends JpaRepository<TaskReviewer, UUID> {

    List<TaskReviewer> findByTaskId(UUID taskId);

    List<TaskReviewer> findByUserId(UUID userId);

    List<TaskReviewer> findByUserIdAndStatus(UUID userId, TaskReviewer.ReviewStatus status);

    Optional<TaskReviewer> findByTaskIdAndUserId(UUID taskId, UUID userId);

    boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);

    void deleteByTaskIdAndUserId(UUID taskId, UUID userId);

    void deleteByTaskId(UUID taskId);

    @Modifying
    @Query("DELETE FROM TaskReviewer tr WHERE tr.task.project.id = :projectId AND tr.user.id = :userId")
    void deleteByProjectIdAndUserId(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}