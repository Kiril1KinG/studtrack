package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.TaskReviewRoundRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskReviewRoundService {

    private final TaskReviewRoundRepository roundRepository;
    private final TaskService taskService;
    private final UserService userService;
    private final ProjectService projectService;

    public List<TaskReviewRound> getRoundsByTask(UUID taskId) {
        return roundRepository.findByTaskIdOrderByRoundNumberAsc(taskId);
    }

    public TaskReviewRound getCurrentRound(UUID taskId) {
        List<TaskReviewRound> rounds = roundRepository.findByTaskIdOrderByRoundNumberDesc(taskId);
        return rounds.isEmpty() ? null : rounds.get(0);
    }

    @Transactional
    public TaskReviewRound createNewRound(UUID taskId, String summaryComment) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());

        User currentUser = userService.getCurrentUser();

        Integer maxRoundNumber = roundRepository.findMaxRoundNumberByTaskId(taskId);
        int nextRoundNumber = (maxRoundNumber != null) ? maxRoundNumber + 1 : 1;

        TaskReviewRound round = TaskReviewRound.builder()
                .task(task)
                .roundNumber(nextRoundNumber)
                .initiator(currentUser)
                .summaryComment(summaryComment)
                .build();

        return roundRepository.save(round);
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