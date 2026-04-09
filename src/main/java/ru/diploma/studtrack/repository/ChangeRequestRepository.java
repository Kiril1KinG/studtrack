package ru.diploma.studtrack.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.diploma.studtrack.model.ChangeRequest;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChangeRequestRepository extends JpaRepository<ChangeRequest, UUID> {

    List<ChangeRequest> findByTaskId(UUID taskId);

    List<ChangeRequest> findByRoundId(UUID roundId);

    List<ChangeRequest> findByTaskIdAndStatus(UUID taskId, ChangeRequest.ChangeRequestStatus status);

    List<ChangeRequest> findByRoundIdAndStatus(UUID roundId, ChangeRequest.ChangeRequestStatus status);

    List<ChangeRequest> findByAuthorId(UUID authorId);

    @Query("SELECT COUNT(cr) FROM ChangeRequest cr WHERE cr.round.id = :roundId AND cr.status = 'OPEN'")
    Integer countOpenByRoundId(@Param("roundId") UUID roundId);

    @Modifying
    @Query("UPDATE ChangeRequest cr SET cr.status = :status WHERE cr.round.id = :roundId")
    void updateStatusByRoundId(@Param("roundId") UUID roundId, @Param("status") ChangeRequest.ChangeRequestStatus status);

    void deleteByTaskId(UUID taskId);

    void deleteByRoundId(UUID roundId);
}