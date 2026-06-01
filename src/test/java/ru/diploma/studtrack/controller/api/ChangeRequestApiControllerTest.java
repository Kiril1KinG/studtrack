package ru.diploma.studtrack.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.diploma.studtrack.dto.request.ChangeRequestCreateRequest;
import ru.diploma.studtrack.dto.request.ChangeRequestUpdateRequest;
import ru.diploma.studtrack.dto.response.ChangeRequestResponse;
import ru.diploma.studtrack.mapper.ChangeRequestMapper;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.service.ChangeRequestService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeRequestApiControllerTest {

    @Mock
    private ChangeRequestService service;
    @Mock
    private ChangeRequestMapper mapper;

    private ChangeRequestApiController controller;

    @BeforeEach
    void setUp() {
        controller = new ChangeRequestApiController(service, mapper);
    }

    @Test
    void getByTaskShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        when(service.getByTask(taskId)).thenReturn(List.of());
        when(mapper.toResponseList(List.of())).thenReturn(List.of());
        var response = controller.getByTask(taskId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void createShouldReturnCreated() {
        UUID taskId = UUID.randomUUID();
        UUID roundId = UUID.randomUUID();
        ChangeRequest cr = ChangeRequest.builder().id(UUID.randomUUID()).build();
        ChangeRequestResponse dto = ChangeRequestResponse.builder().id(cr.getId()).build();
        when(service.create(taskId, roundId, "text")).thenReturn(cr);
        when(mapper.toResponse(cr)).thenReturn(dto);

        var response = controller.create(taskId, roundId, ChangeRequestCreateRequest.builder().content("text").build());
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void deleteShouldReturnNoContent() {
        UUID id = UUID.randomUUID();
        var response = controller.delete(id);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(service).delete(id);
    }

    @Test
    void getByRoundAndOpenByRoundShouldReturnOk() {
        UUID roundId = UUID.randomUUID();
        when(service.getByRound(roundId)).thenReturn(List.of());
        when(service.getOpenByRound(roundId)).thenReturn(List.of());
        when(mapper.toResponseList(List.of())).thenReturn(List.of());
        assertEquals(HttpStatus.OK, controller.getByRound(roundId).getStatusCode());
        assertEquals(HttpStatus.OK, controller.getOpenByRound(roundId).getStatusCode());
    }

    @Test
    void updateResolveAndReopenShouldReturnOk() {
        UUID id = UUID.randomUUID();
        ChangeRequest cr = ChangeRequest.builder().id(id).build();
        ChangeRequestResponse dto = ChangeRequestResponse.builder().id(id).build();
        when(service.updateContent(id, "upd")).thenReturn(cr);
        when(service.markAsResolved(id)).thenReturn(cr);
        when(service.markAsOpen(id)).thenReturn(cr);
        when(mapper.toResponse(cr)).thenReturn(dto);
        assertEquals(HttpStatus.OK, controller.update(id, ChangeRequestUpdateRequest.builder().content("upd").build()).getStatusCode());
        assertEquals(HttpStatus.OK, controller.markAsResolved(id).getStatusCode());
        assertEquals(HttpStatus.OK, controller.markAsOpen(id).getStatusCode());
    }
}

