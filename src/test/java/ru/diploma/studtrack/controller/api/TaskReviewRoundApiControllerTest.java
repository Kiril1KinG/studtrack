package ru.diploma.studtrack.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.diploma.studtrack.dto.response.TaskReviewRoundResponse;
import ru.diploma.studtrack.mapper.TaskReviewRoundMapper;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.service.TaskReviewRoundService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReviewRoundApiControllerTest {

    @Mock
    private TaskReviewRoundService service;
    @Mock
    private TaskReviewRoundMapper mapper;

    private TaskReviewRoundApiController controller;

    @BeforeEach
    void setUp() {
        controller = new TaskReviewRoundApiController(service, mapper);
    }

    @Test
    void getRoundsShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        when(service.getRoundsByTask(taskId)).thenReturn(List.of());
        when(mapper.toResponseList(List.of())).thenReturn(List.of());
        var response = controller.getRounds(taskId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void getCurrentRoundShouldReturnNotFoundWhenNull() {
        UUID taskId = UUID.randomUUID();
        when(service.getCurrentRound(taskId)).thenReturn(null);
        var response = controller.getCurrentRound(taskId);
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void createRoundShouldReturnCreated() {
        UUID taskId = UUID.randomUUID();
        TaskReviewRound round = TaskReviewRound.builder().id(UUID.randomUUID()).roundNumber(1).build();
        TaskReviewRoundResponse dto = TaskReviewRoundResponse.builder().id(round.getId()).roundNumber(1).build();
        when(service.createNewRound(taskId, "sum")).thenReturn(round);
        when(mapper.toResponse(round)).thenReturn(dto);
        var response = controller.createRound(taskId, "sum");
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }
}

