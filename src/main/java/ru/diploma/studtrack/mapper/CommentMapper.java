package ru.diploma.studtrack.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import ru.diploma.studtrack.dto.request.CommentCreateRequest;
import ru.diploma.studtrack.dto.request.CommentUpdateRequest;
import ru.diploma.studtrack.dto.response.CommentResponse;
import ru.diploma.studtrack.model.Comment;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {UserMapper.class})
public interface CommentMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "task", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    Comment toEntity(CommentCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "task", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    void updateEntity(CommentUpdateRequest request, @MappingTarget Comment comment);

    @Mapping(target = "roundId", source = "round.id")
    CommentResponse toResponse(Comment comment);

    List<CommentResponse> toResponseList(List<Comment> comments);
}