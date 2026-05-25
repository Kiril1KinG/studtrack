package ru.diploma.studtrack.controller.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.diploma.studtrack.dto.request.ChangeRequestCreateRequest;
import ru.diploma.studtrack.dto.request.ChangeRequestUpdateRequest;
import ru.diploma.studtrack.dto.response.ChangeRequestResponse;
import ru.diploma.studtrack.mapper.ChangeRequestMapper;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.service.ChangeRequestService;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ChangeRequestApiController {

    private final ChangeRequestService changeRequestService;
    private final ChangeRequestMapper changeRequestMapper;

    @GetMapping("/tasks/{taskId}/change-requests")
    public ResponseEntity<List<ChangeRequestResponse>> getByTask(@PathVariable UUID taskId) {
        log.info("Запрос на получение замечаний задачи id: {}", taskId);
        List<ChangeRequest> requests = changeRequestService.getByTask(taskId);
        log.debug("Найдено {} замечаний", requests.size());
        return ResponseEntity.ok(changeRequestMapper.toResponseList(requests));
    }

    @GetMapping("/rounds/{roundId}/change-requests")
    public ResponseEntity<List<ChangeRequestResponse>> getByRound(@PathVariable UUID roundId) {
        log.info("Запрос на получение замечаний итерации id: {}", roundId);
        List<ChangeRequest> requests = changeRequestService.getByRound(roundId);
        log.debug("Найдено {} замечаний", requests.size());
        return ResponseEntity.ok(changeRequestMapper.toResponseList(requests));
    }

    @GetMapping("/rounds/{roundId}/change-requests/open")
    public ResponseEntity<List<ChangeRequestResponse>> getOpenByRound(@PathVariable UUID roundId) {
        log.info("Запрос на получение открытых замечаний итерации id: {}", roundId);
        List<ChangeRequest> requests = changeRequestService.getOpenByRound(roundId);
        log.debug("Найдено {} открытых замечаний", requests.size());
        return ResponseEntity.ok(changeRequestMapper.toResponseList(requests));
    }

    @PostMapping("/tasks/{taskId}/rounds/{roundId}/change-requests")
    public ResponseEntity<ChangeRequestResponse> create(
            @PathVariable UUID taskId,
            @PathVariable UUID roundId,
            @Valid @RequestBody ChangeRequestCreateRequest request) {
        log.info("Запрос на создание замечания в задаче {}, итерации {}: content='{}'",
                taskId, roundId, request.getContent());
        ChangeRequest changeRequest = changeRequestService.create(taskId, roundId, request.getContent());
        log.info("Замечание создано с id: {}", changeRequest.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(changeRequestMapper.toResponse(changeRequest));
    }

    @PutMapping("/change-requests/{id}")
    public ResponseEntity<ChangeRequestResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRequestUpdateRequest request) {
        log.info("Запрос на обновление замечания id: {}", id);
        ChangeRequest changeRequest = changeRequestService.updateContent(id, request.getContent());
        log.info("Замечание обновлено");
        return ResponseEntity.ok(changeRequestMapper.toResponse(changeRequest));
    }

    @PatchMapping("/change-requests/{id}/resolve")
    public ResponseEntity<ChangeRequestResponse> markAsResolved(@PathVariable UUID id) {
        log.info("Запрос на отметку замечания id: {} как исправленного", id);
        ChangeRequest changeRequest = changeRequestService.markAsResolved(id);
        log.info("Замечание отмечено как исправленное");
        return ResponseEntity.ok(changeRequestMapper.toResponse(changeRequest));
    }

    @PatchMapping("/change-requests/{id}/reopen")
    public ResponseEntity<ChangeRequestResponse> markAsOpen(@PathVariable UUID id) {
        log.info("Запрос на переоткрытие замечания id: {}", id);
        ChangeRequest changeRequest = changeRequestService.markAsOpen(id);
        log.info("Замечание переоткрыто");
        return ResponseEntity.ok(changeRequestMapper.toResponse(changeRequest));
    }

    @DeleteMapping("/change-requests/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        log.info("Запрос на удаление замечания id: {}", id);
        changeRequestService.delete(id);
        log.info("Замечание удалено");
        return ResponseEntity.noContent().build();
    }
}
