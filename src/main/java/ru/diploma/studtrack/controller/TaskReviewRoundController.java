package ru.diploma.studtrack.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diploma.studtrack.dto.response.TaskReviewRoundResponse;
import ru.diploma.studtrack.mapper.TaskReviewRoundMapper;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.service.TaskReviewRoundService;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tasks/{taskId}/rounds")
@RequiredArgsConstructor
public class TaskReviewRoundController {

    private final TaskReviewRoundService roundService;
    private final TaskReviewRoundMapper roundMapper;

    @GetMapping
    public ResponseEntity<List<TaskReviewRoundResponse>> getRounds(@PathVariable UUID taskId) {
        log.info("Запрос на получение итераций ревью задачи id: {}", taskId);
        List<TaskReviewRound> rounds = roundService.getRoundsByTask(taskId);
        log.debug("Найдено {} итераций", rounds.size());
        return ResponseEntity.ok(roundMapper.toResponseList(rounds));
    }

    @GetMapping("/current")
    public ResponseEntity<TaskReviewRoundResponse> getCurrentRound(@PathVariable UUID taskId) {
        log.info("Запрос на получение текущей итерации ревью задачи id: {}", taskId);
        TaskReviewRound round = roundService.getCurrentRound(taskId);
        if (round == null) {
            log.debug("Текущая итерация не найдена");
            return ResponseEntity.notFound().build();
        }
        log.debug("Текущая итерация: №{}", round.getRoundNumber());
        return ResponseEntity.ok(roundMapper.toResponse(round));
    }

    @PostMapping
    public ResponseEntity<TaskReviewRoundResponse> createRound(
            @PathVariable UUID taskId,
            @RequestBody(required = false) String summaryComment) {
        log.info("Запрос на создание новой итерации ревью для задачи id: {}, comment={}", 
                taskId, summaryComment);
        TaskReviewRound round = roundService.createNewRound(taskId, summaryComment);
        log.info("Итерация создана: №{}", round.getRoundNumber());
        return ResponseEntity.status(HttpStatus.CREATED).body(roundMapper.toResponse(round));
    }

    @PutMapping("/{roundId}/summary")
    public ResponseEntity<TaskReviewRoundResponse> updateSummary(
            @PathVariable UUID roundId,
            @RequestBody String summaryComment) {
        log.info("Запрос на обновление комментария итерации id: {}", roundId);
        TaskReviewRound round = roundService.updateSummary(roundId, summaryComment);
        log.info("Комментарий итерации обновлён");
        return ResponseEntity.ok(roundMapper.toResponse(round));
    }
}