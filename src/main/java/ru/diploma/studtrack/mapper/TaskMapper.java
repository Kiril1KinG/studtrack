package ru.diploma.studtrack.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;
import ru.diploma.studtrack.dto.request.TaskCreateRequest;
import ru.diploma.studtrack.dto.request.TaskUpdateRequest;
import ru.diploma.studtrack.dto.response.TaskResponse;
import ru.diploma.studtrack.dto.response.UserResponse;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAssignee;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = {UserMapper.class, TaskReviewerMapper.class})
public interface TaskMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "status", constant = "BACKLOG")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "reviewers", ignore = true)
    @Mapping(target = "assignees", ignore = true)
    @Mapping(target = "reviewRounds", ignore = true)
    @Mapping(target = "changeRequests", ignore = true)
    Task toEntity(TaskCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "project", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "reviewRequired", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "comments", ignore = true)
    @Mapping(target = "reviewers", ignore = true)
    @Mapping(target = "assignees", ignore = true)
    @Mapping(target = "reviewRounds", ignore = true)
    @Mapping(target = "changeRequests", ignore = true)
    void updateEntity(TaskUpdateRequest request, @MappingTarget Task task);

    @Mapping(target = "projectId", source = "project.id")
    @Mapping(target = "commentCount", expression = "java(task.getComments() != null ? task.getComments().size() : 0)")
    @Mapping(target = "assignee", expression = "java(primaryAssignee(task))")
    @Mapping(target = "assignees", expression = "java(allAssignees(task))")
    TaskResponse toResponse(Task task);

    List<TaskResponse> toResponseList(List<Task> tasks);

    default UserResponse primaryAssignee(Task task) {
        if (task.getAssignees() == null || task.getAssignees().isEmpty()) {
            return null;
        }
        TaskAssignee first = task.getAssignees().iterator().next();
        return UserResponse.builder()
                .id(first.getUser().getId())
                .email(first.getUser().getEmail())
                .fullName(first.getUser().getFullName())
                .createdAt(first.getUser().getCreatedAt())
                .build();
    }

    default List<UserResponse> allAssignees(Task task) {
        if (task.getAssignees() == null || task.getAssignees().isEmpty()) {
            return List.of();
        }
        return task.getAssignees().stream()
                .map(TaskAssignee::getUser)
                .map(user -> UserResponse.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .fullName(user.getFullName())
                        .createdAt(user.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }
}