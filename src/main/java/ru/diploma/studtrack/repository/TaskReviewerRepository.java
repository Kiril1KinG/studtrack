package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.TaskReviewer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskReviewerRepository extends JpaRepository<TaskReviewer, UUID> {

    List<TaskReviewer> findByTaskId(UUID taskId);

    List<TaskReviewer> findByUserId(UUID userId);

    Optional<TaskReviewer> findByTaskIdAndUserId(UUID taskId, UUID userId);

    boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);

    void deleteByTaskIdAndUserId(UUID taskId, UUID userId);
}