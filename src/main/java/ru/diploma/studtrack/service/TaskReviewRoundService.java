package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskReviewRoundRepository;
import ru.diploma.studtrack.repository.TaskReviewerRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskReviewRoundService {

    private final TaskReviewRoundRepository roundRepository;
    private final TaskReviewerRepository taskReviewerRepository;
    private final TaskService taskService;
    private final TaskAssigneeService taskAssigneeService;
    private final UserService userService;
    private final ProjectService projectService;
    private final NotificationService notificationService;
    private final TaskHistoryService taskHistoryService;

    public List<TaskReviewRound> getRoundsByTask(UUID taskId) {
        return roundRepository.findByTaskIdOrderByRoundNumberAsc(taskId);
    }

    public boolean canStartNewRound(UUID taskId) {
        List<TaskReviewRound> rounds = roundRepository.findByTaskIdOrderByRoundNumberDesc(taskId);
        if (rounds.isEmpty()) return true;
        TaskReviewRound lastRound = rounds.get(0);
        return lastRound.getStatus() != TaskReviewRound.RoundStatus.OPEN;
    }

    public boolean isLastRoundCompleted(UUID taskId) {
        List<TaskReviewRound> rounds = roundRepository.findByTaskIdOrderByRoundNumberDesc(taskId);
        if (rounds.isEmpty()) return false;
        return rounds.get(0).getStatus() == TaskReviewRound.RoundStatus.COMPLETED;
    }

    public List<TaskReviewRound> getRoundsForView(UUID taskId) {
        List<TaskReviewRound> rounds = roundRepository.findByTaskIdOrderByRoundNumberDesc(taskId);
        rounds.forEach(round -> {
            Hibernate.initialize(round.getInitiator());
            Hibernate.initialize(round.getChangeRequests());
            round.getChangeRequests().forEach(cr -> Hibernate.initialize(cr.getAuthor()));
        });
        return rounds;
    }

    public TaskReviewRound getCurrentRound(UUID taskId) {
        List<TaskReviewRound> rounds = roundRepository.findByTaskIdOrderByRoundNumberDesc(taskId);
        return rounds.isEmpty() ? null : rounds.get(0);
    }

    @Transactional
    public TaskReviewRound createNewRound(UUID taskId, String summaryComment) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        if (!task.isReviewRequired()) {
            throw new InvalidStateException(
                    "Нельзя начать новый раунд",
                    "для задачи отключено ревью",
                    "включите флаг требуемого ревью"
            );
        }

        UUID currentUserId = userService.getCurrentUserId();
        if (!taskReviewerRepository.existsByTaskIdAndUserId(taskId, currentUserId)) {
            throw new AccessDeniedException("Новый раунд ревью может начать только ревьюер задачи");
        }

        if (!canStartNewRound(taskId)) {
            throw new InvalidStateException(
                    "Нельзя начать новый раунд",
                    "предыдущий раунд ещё не завершён исполнителем",
                    "дождитесь завершения текущего раунда"
            );
        }

        User currentUser = userService.getCurrentUser();
        Integer maxRoundNumber = roundRepository.findMaxRoundNumberByTaskId(taskId);
        int nextRoundNumber = (maxRoundNumber != null) ? maxRoundNumber + 1 : 1;

        TaskReviewRound round = TaskReviewRound.builder()
                .task(task)
                .roundNumber(nextRoundNumber)
                .initiator(currentUser)
                .summaryComment(summaryComment)
                .status(TaskReviewRound.RoundStatus.OPEN)
                .build();

        TaskReviewRound saved = roundRepository.save(round);
        taskHistoryService.recordEvent(task, currentUser, TaskHistory.EventType.REVIEW_ROUND_CREATED, Map.of(
                "roundId", saved.getId(),
                "roundNumber", saved.getRoundNumber()
        ));
        return saved;
    }

    @Transactional
    public TaskReviewRound completeRound(UUID roundId) {
        TaskReviewRound round = findById(roundId);
        Task task = round.getTask();
        if (!task.isReviewRequired()) {
            throw new InvalidStateException(
                    "Нельзя завершить раунд",
                    "для задачи отключено ревью",
                    "проверьте актуальность статуса задачи"
            );
        }

        User actor = userService.getCurrentUser();
        UUID currentUserId = actor.getId();
        if (!taskAssigneeService.isAssignee(task.getId(), currentUserId)) {
            throw new AccessDeniedException("Завершить раунд может только исполнитель задачи");
        }
        if (round.getStatus() != TaskReviewRound.RoundStatus.OPEN) {
            throw new InvalidStateException(
                    "Нельзя завершить раунд",
                    "раунд уже не находится в статусе OPEN",
                    "обновите страницу и проверьте актуальные данные"
            );
        }

        List<TaskReviewer> reviewers = taskReviewerRepository.findByTaskId(task.getId());
        if (reviewers.isEmpty()) {
            throw new InvalidStateException(
                    "Нельзя завершить раунд",
                    "для задачи не назначены ревьюеры",
                    "назначьте хотя бы одного ревьюера"
            );
        }

        round.setStatus(TaskReviewRound.RoundStatus.COMPLETED);
        List<TaskReviewer> reviewersToRecheck = reviewers.stream()
                .filter(reviewer -> reviewer.getStatus() != TaskReviewer.ReviewStatus.APPROVED)
                .toList();
        reviewersToRecheck.forEach(reviewer -> {
            reviewer.setStatus(TaskReviewer.ReviewStatus.PENDING);
            reviewer.setComment(null);
        });
        taskReviewerRepository.saveAll(reviewers);
        notificationService.notifyRoundRecheck(task, actor, reviewersToRecheck);
        TaskReviewRound saved = roundRepository.save(round);
        taskHistoryService.recordEvent(task, actor, TaskHistory.EventType.REVIEW_ROUND_COMPLETED, Map.of(
                "roundId", saved.getId(),
                "roundNumber", saved.getRoundNumber()
        ));
        return saved;
    }

    @Transactional
    public TaskReviewRound updateSummary(UUID roundId, String summaryComment) {
        TaskReviewRound round = findById(roundId);
        round.setSummaryComment(summaryComment);
        return roundRepository.save(round);
    }

    public TaskReviewRound findById(UUID id) {
        return roundRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Итерация ревью", id));
    }
}