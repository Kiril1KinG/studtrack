package ru.diploma.studtrack.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.diploma.studtrack.dto.response.TaskReviewerResponse;
import ru.diploma.studtrack.model.TaskReviewer;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {UserMapper.class})
public interface TaskReviewerMapper {

    TaskReviewerResponse toResponse(TaskReviewer taskReviewer);

    List<TaskReviewerResponse> toResponseList(List<TaskReviewer> taskReviewers);
}