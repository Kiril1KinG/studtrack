package ru.diploma.studtrack.controller.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import ru.diploma.studtrack.dto.request.ReviewSubmitRequest;
import ru.diploma.studtrack.dto.response.TaskReviewerResponse;
import ru.diploma.studtrack.mapper.TaskReviewerMapper;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.service.TaskReviewerService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReviewerApiControllerTest {

    @Mock
    private TaskReviewerService service;
    @Mock
    private TaskReviewerMapper mapper;

    private TaskReviewerApiController controller;

    @BeforeEach
    void setUp() {
        controller = new TaskReviewerApiController(service, mapper);
    }

    @Test
    void getReviewersShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        when(service.getReviewersByTask(taskId)).thenReturn(List.of());
        when(mapper.toResponseList(List.of())).thenReturn(List.of());
        var response = controller.getReviewers(taskId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void addReviewerShouldReturnCreated() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TaskReviewer reviewer = TaskReviewer.builder().id(UUID.randomUUID()).build();
        TaskReviewerResponse dto = TaskReviewerResponse.builder().id(reviewer.getId()).build();
        when(service.addReviewer(taskId, reviewerId)).thenReturn(reviewer);
        when(mapper.toResponse(reviewer)).thenReturn(dto);

        var response = controller.addReviewer(taskId, reviewerId);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void submitAndDeleteShouldReturnExpectedStatuses() {
        UUID taskId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        TaskReviewer reviewer = TaskReviewer.builder().id(UUID.randomUUID()).status(TaskReviewer.ReviewStatus.APPROVED).build();
        TaskReviewerResponse dto = TaskReviewerResponse.builder().id(reviewer.getId()).status(TaskReviewer.ReviewStatus.APPROVED).build();
        when(service.submitReview(taskId, reviewerId, TaskReviewer.ReviewStatus.APPROVED, "ok")).thenReturn(reviewer);
        when(mapper.toResponse(reviewer)).thenReturn(dto);

        var submit = controller.submitReview(taskId, reviewerId,
                ReviewSubmitRequest.builder().status(TaskReviewer.ReviewStatus.APPROVED).comment("ok").build());
        assertEquals(HttpStatus.OK, submit.getStatusCode());

        var delete = controller.removeReviewer(taskId, reviewerId);
        assertEquals(HttpStatus.NO_CONTENT, delete.getStatusCode());
        verify(service).removeReviewer(taskId, reviewerId);
    }

    @Test
    void getPendingReviewsShouldReturnOk() {
        UUID taskId = UUID.randomUUID();
        when(service.getPendingReviewsForCurrentUserByTask(taskId)).thenReturn(List.of());
        when(mapper.toResponseList(List.of())).thenReturn(List.of());
        var response = controller.getPendingReviews(taskId);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }
}

