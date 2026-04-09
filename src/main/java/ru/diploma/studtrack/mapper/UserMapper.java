package ru.diploma.studtrack.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import ru.diploma.studtrack.dto.response.UserResponse;
import ru.diploma.studtrack.model.User;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface UserMapper {

    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);
}