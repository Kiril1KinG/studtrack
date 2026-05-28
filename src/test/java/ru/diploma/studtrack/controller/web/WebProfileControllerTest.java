package ru.diploma.studtrack.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import ru.diploma.studtrack.model.Project;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.service.ProjectService;
import ru.diploma.studtrack.service.TaskService;
import ru.diploma.studtrack.service.UserService;
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebProfileControllerTest {

    @Mock private UserService userService;
    @Mock private ProjectService projectService;
    @Mock private TaskService taskService;
    @Mock private WebErrorMessageService webErrorMessageService;

    private WebProfileController controller;

    @BeforeEach
    void setUp() {
        controller = new WebProfileController(userService, projectService, taskService, webErrorMessageService);
    }

    @Test
    void viewProfileShouldPopulateModel() {
        User user = User.builder().id(UUID.randomUUID()).build();
        Task openTask = Task.builder().status(Task.TaskStatus.IN_PROGRESS).build();
        Task doneTask = Task.builder().status(Task.TaskStatus.DONE).build();
        Model model = new ExtendedModelMap();
        when(userService.getCurrentUser()).thenReturn(user);
        when(userService.getAvatarUrl(user)).thenReturn("url");
        when(projectService.getMyProjects()).thenReturn(List.of(Project.builder().build()));
        when(taskService.getAssignedToMe()).thenReturn(List.of(openTask, doneTask));

        String view = controller.viewProfile(model);
        assertEquals("profile/view", view);
        assertEquals(user, model.getAttribute("user"));
        assertEquals("url", model.getAttribute("avatarUrl"));
    }

    @Test
    void updateProfileShouldSetSuccessFlash() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        UUID id = UUID.randomUUID();
        when(userService.getCurrentUser()).thenReturn(User.builder().id(id).build());

        String view = controller.updateProfile("Иванов", "Иван", "Иваныч", redirect);
        assertEquals("redirect:/profile", view);
        assertEquals("Профиль обновлён", redirect.getFlashAttributes().get("successMessage"));
        verify(userService).update(any(UUID.class), any(User.class));
    }

    @Test
    void changePasswordShouldSetFriendlyErrorOnFailure() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        when(userService.getCurrentUserId()).thenReturn(UUID.randomUUID());
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("bad"))
                .when(userService).changePassword(any(UUID.class), anyString(), anyString());

        String view = controller.changePassword("old", "new", redirect);
        assertEquals("redirect:/profile", view);
        assertEquals("friendly", redirect.getFlashAttributes().get("errorMessage"));
    }

    @Test
    void avatarMethodsShouldDelegateAndRedirect() {
        RedirectAttributesModelMap redirect = new RedirectAttributesModelMap();
        UUID id = UUID.randomUUID();
        when(userService.getCurrentUserId()).thenReturn(id);
        MockMultipartFile avatar = new MockMultipartFile("avatar", "a.png", "image/png", new byte[]{1});

        String updateView = controller.updateAvatar(avatar, redirect);
        String deleteView = controller.deleteAvatar(redirect);

        assertEquals("redirect:/profile", updateView);
        assertEquals("redirect:/profile", deleteView);
        verify(userService).updateAvatar(id, avatar);
        verify(userService).deleteAvatar(id);
    }
}

