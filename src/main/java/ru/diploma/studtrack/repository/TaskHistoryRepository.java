package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.TaskHistory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TaskHistoryRepository extends JpaRepository<TaskHistory, UUID> {

    List<TaskHistory> findByTaskIdOrderByCreatedAtDesc(UUID taskId);

    @Query("""
            select h
            from TaskHistory h
            where h.task.project.id = :projectId
              and h.eventType = :eventType
            order by h.createdAt asc
            """)
    List<TaskHistory> findStatusHistoryByProject(@Param("projectId") UUID projectId,
                                                 @Param("eventType") TaskHistory.EventType eventType);

    @Query("""
            select h
            from TaskHistory h
            where h.task.project.id = :projectId
              and h.eventType = :eventType
              and h.createdAt >= :from
            order by h.createdAt asc
            """)
    List<TaskHistory> findStatusHistoryByProjectAndCreatedAtAfter(@Param("projectId") UUID projectId,
                                                                  @Param("eventType") TaskHistory.EventType eventType,
                                                                  @Param("from") LocalDateTime from);
}
