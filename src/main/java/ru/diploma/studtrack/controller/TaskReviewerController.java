package ru.diploma.studtrack.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diploma.studtrack.dto.request.ReviewSubmitRequest;
import ru.diploma.studtrack.dto.response.TaskReviewerResponse;
import ru.diploma.studtrack.mapper.TaskReviewerMapper;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.service.TaskReviewerService;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tasks/{taskId}/reviewers")
@RequiredArgsConstructor
public class TaskReviewerController {

    private final TaskReviewerService taskReviewerService;
    private final TaskReviewerMapper taskReviewerMapper;

    @GetMapping
    public ResponseEntity<List<TaskReviewerResponse>> getReviewers(@PathVariable UUID taskId) {
        log.info("Запрос на получение ревьюеров задачи id: {}", taskId);
        List<TaskReviewer> reviewers = taskReviewerService.getReviewersByTask(taskId);
        log.debug("Найдено {} ревьюеров", reviewers.size());
        return ResponseEntity.ok(taskReviewerMapper.toResponseList(reviewers));
    }

    @GetMapping("/pending")
    public ResponseEntity<List<TaskReviewerResponse>> getPendingReviews() {
        log.info("Запрос на получение ожидающих ревью для текущего пользователя");
        List<TaskReviewer> pending = taskReviewerService.getPendingReviewsForCurrentUser();
        log.debug("Найдено {} ожидающих ревью", pending.size());
        return ResponseEntity.ok(taskReviewerMapper.toResponseList(pending));
    }

    @PostMapping("/{reviewerId}")
    public ResponseEntity<TaskReviewerResponse> addReviewer(
            @PathVariable UUID taskId,
            @PathVariable UUID reviewerId) {
        log.info("Запрос на добавление ревьюера {} к задаче {}", reviewerId, taskId);
        TaskReviewer reviewer = taskReviewerService.addReviewer(taskId, reviewerId);
        log.info("Ревьюер добавлен: {}", reviewer.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(taskReviewerMapper.toResponse(reviewer));
    }

    @DeleteMapping("/{reviewerId}")
    public ResponseEntity<Void> removeReviewer(
            @PathVariable UUID taskId,
            @PathVariable UUID reviewerId) {
        log.info("Запрос на удаление ревьюера {} из задачи {}", reviewerId, taskId);
        taskReviewerService.removeReviewer(taskId, reviewerId);
        log.info("Ревьюер удалён");
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{reviewerId}/submit")
    public ResponseEntity<TaskReviewerResponse> submitReview(
            @PathVariable UUID taskId,
            @PathVariable UUID reviewerId,
            @Valid @RequestBody ReviewSubmitRequest request) {
        log.info("Запрос на отправку ревью для задачи {} от ревьюера {}: status={}, hasComment={}",
                taskId, reviewerId, request.getStatus(), request.getComment() != null);
        TaskReviewer reviewer = taskReviewerService.submitReview(
                taskId, reviewerId, request.getStatus(), request.getComment());
        log.info("Ревью отправлено: {}", reviewer.getStatus());
        return ResponseEntity.ok(taskReviewerMapper.toResponse(reviewer));
    }
}