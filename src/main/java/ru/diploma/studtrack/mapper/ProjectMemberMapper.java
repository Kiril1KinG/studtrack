package ru.diploma.studtrack.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.diploma.studtrack.dto.response.ProjectMemberResponse;
import ru.diploma.studtrack.model.ProjectMember;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {UserMapper.class})
public interface ProjectMemberMapper {

    ProjectMemberResponse toResponse(ProjectMember projectMember);

    List<ProjectMemberResponse> toResponseList(List<ProjectMember> projectMembers);
}