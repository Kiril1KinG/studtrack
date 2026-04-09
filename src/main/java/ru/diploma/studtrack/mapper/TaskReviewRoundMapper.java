package ru.diploma.studtrack.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import ru.diploma.studtrack.dto.response.TaskReviewRoundResponse;
import ru.diploma.studtrack.model.TaskReviewRound;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {UserMapper.class, ChangeRequestMapper.class, CommentMapper.class})
public interface TaskReviewRoundMapper {

    @Mapping(target = "changeRequests", source = "changeRequests")
    @Mapping(target = "comments", source = "comments")
    TaskReviewRoundResponse toResponse(TaskReviewRound round);

    List<TaskReviewRoundResponse> toResponseList(List<TaskReviewRound> rounds);
}