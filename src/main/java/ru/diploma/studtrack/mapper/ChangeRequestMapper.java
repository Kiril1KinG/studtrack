package ru.diploma.studtrack.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import ru.diploma.studtrack.dto.request.ChangeRequestCreateRequest;
import ru.diploma.studtrack.dto.request.ChangeRequestUpdateRequest;
import ru.diploma.studtrack.dto.response.ChangeRequestResponse;
import ru.diploma.studtrack.model.ChangeRequest;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {UserMapper.class})
public interface ChangeRequestMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "task", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "status", constant = "OPEN")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ChangeRequest toEntity(ChangeRequestCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "task", ignore = true)
    @Mapping(target = "author", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    void updateEntity(ChangeRequestUpdateRequest request, @MappingTarget ChangeRequest changeRequest);

    @Mapping(target = "roundId", source = "round.id")
    ChangeRequestResponse toResponse(ChangeRequest changeRequest);

    List<ChangeRequestResponse> toResponseList(List<ChangeRequest> changeRequests);
}