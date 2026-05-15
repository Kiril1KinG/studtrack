package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.TaskAssignee;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, UUID> {

    List<TaskAssignee> findByTaskId(UUID taskId);

    List<TaskAssignee> findByUserId(UUID userId);

    Optional<TaskAssignee> findByTaskIdAndUserId(UUID taskId, UUID userId);

    boolean existsByTaskIdAndUserId(UUID taskId, UUID userId);

    void deleteByTaskIdAndUserId(UUID taskId, UUID userId);

    void deleteByTaskId(UUID taskId);

    void deleteByTaskProjectIdAndUserId(UUID projectId, UUID userId);
}
