package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.TaskReviewRound;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskReviewRoundRepository extends JpaRepository<TaskReviewRound, UUID> {

    List<TaskReviewRound> findByTaskIdOrderByRoundNumberAsc(UUID taskId);

    List<TaskReviewRound> findByTaskIdOrderByRoundNumberDesc(UUID taskId);

    @Query("SELECT MAX(r.roundNumber) FROM TaskReviewRound r WHERE r.task.id = :taskId")
    Integer findMaxRoundNumberByTaskId(@Param("taskId") UUID taskId);

    Integer countByTaskId(UUID taskId);

    void deleteByTaskId(UUID taskId);
}