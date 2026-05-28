package ru.diploma.studtrack.controller.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import ru.diploma.studtrack.service.NotificationService;
import ru.diploma.studtrack.service.WebErrorMessageService;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebNotificationControllerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private WebErrorMessageService webErrorMessageService;

    private WebNotificationController controller;

    @BeforeEach
    void setUp() {
        controller = new WebNotificationController(notificationService, webErrorMessageService);
    }

    @Test
    void unreadCountShouldRenderBadge() {
        Model model = new ExtendedModelMap();
        when(notificationService.getUnreadCountForCurrentUser()).thenReturn(5L);

        String view = controller.unreadCount(model);

        assertEquals("fragments/notifications :: unreadBadge", view);
        assertEquals(5L, model.getAttribute("unreadCount"));
    }

    @Test
    void dropdownShouldReturnFallbackOnError() {
        Model model = new ExtendedModelMap();
        when(notificationService.getRecentForCurrentUser()).thenThrow(new RuntimeException("boom"));
        when(webErrorMessageService.resolve(any(), anyString())).thenReturn("friendly");

        String view = controller.dropdown(model);

        assertEquals("fragments/notifications :: dropdownList", view);
        assertEquals(0, model.getAttribute("unreadCount"));
        assertEquals("friendly", model.getAttribute("notificationErrorMessage"));
    }

    @Test
    void markReadShouldRefreshDropdown() {
        Model model = new ExtendedModelMap();
        UUID id = UUID.randomUUID();
        when(notificationService.getRecentForCurrentUser()).thenReturn(List.of());
        when(notificationService.getUnreadCountForCurrentUser()).thenReturn(0L);

        String view = controller.markRead(id, model);

        assertEquals("fragments/notifications :: dropdownList", view);
    }
}

