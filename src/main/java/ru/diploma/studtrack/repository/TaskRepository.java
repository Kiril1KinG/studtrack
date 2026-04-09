package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.Task;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId AND t.assignee.id = :userId")
    List<Task> findByProjectIdAndAssigneeId(@Param("projectId") UUID projectId,
                                            @Param("userId") UUID userId);

    List<Task> findByAssigneeId(UUID assigneeId);
}