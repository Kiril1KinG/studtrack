package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.exception.AccessDeniedException;
import ru.diploma.studtrack.exception.InvalidStateException;
import ru.diploma.studtrack.exception.NotFoundException;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskReviewRound;
import ru.diploma.studtrack.model.User;
import ru.diploma.studtrack.repository.ChangeRequestRepository;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChangeRequestService {

    private final ChangeRequestRepository changeRequestRepository;
    private final TaskReviewRoundService roundService;
    private final TaskService taskService;
    private final TaskAssigneeService taskAssigneeService;
    private final UserService userService;
    private final ProjectService projectService;
    private final NotificationService notificationService;

    public List<ChangeRequest> getByTask(UUID taskId) {
        return changeRequestRepository.findByTaskId(taskId);
    }

    public List<ChangeRequest> getByRound(UUID roundId) {
        return changeRequestRepository.findByRoundId(roundId);
    }

    public List<ChangeRequest> getOpenByRound(UUID roundId) {
        return changeRequestRepository.findByRoundIdAndStatus(roundId, ChangeRequest.ChangeRequestStatus.OPEN);
    }

    public ChangeRequest findById(UUID id) {
        return changeRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Замечание", id));
    }

    @Transactional
    public ChangeRequest create(UUID taskId, UUID roundId, String content) {
        Task task = taskService.findById(taskId);
        projectService.checkMembership(task.getProject().getId());
        if (!task.isReviewRequired()) {
            throw new InvalidStateException(
                    "Нельзя добавить замечание",
                    "для задачи отключено ревью",
                    "включите ревью для задачи"
            );
        }

        TaskReviewRound round = roundService.findById(roundId);
        User currentUser = userService.getCurrentUser();

        ChangeRequest changeRequest = ChangeRequest.builder()
                .round(round)
                .task(task)
                .author(currentUser)
                .content(content)
                .status(ChangeRequest.ChangeRequestStatus.OPEN)
                .build();

        ChangeRequest saved = changeRequestRepository.save(changeRequest);
        notificationService.notifyChangeRequestCreated(saved, currentUser);
        return saved;
    }

    @Transactional
    public ChangeRequest updateContent(UUID id, String content) {
        ChangeRequest changeRequest = findById(id);
        checkTaskMembership(changeRequest);
        changeRequest.setContent(content);
        return changeRequestRepository.save(changeRequest);
    }

    @Transactional
    public ChangeRequest markAsResolved(UUID id) {
        ChangeRequest changeRequest = findById(id);
        checkIsAssignee(changeRequest);
        changeRequest.setStatus(ChangeRequest.ChangeRequestStatus.RESOLVED);
        return changeRequestRepository.save(changeRequest);
    }

    @Transactional
    public ChangeRequest markAsRejected(UUID id) {
        ChangeRequest changeRequest = findById(id);
        checkIsAssignee(changeRequest);
        changeRequest.setStatus(ChangeRequest.ChangeRequestStatus.REJECTED);
        return changeRequestRepository.save(changeRequest);
    }

    @Transactional
    public ChangeRequest markAsOpen(UUID id) {
        ChangeRequest changeRequest = findById(id);
        checkIsAssignee(changeRequest);
        changeRequest.setStatus(ChangeRequest.ChangeRequestStatus.OPEN);
        return changeRequestRepository.save(changeRequest);
    }

    @Transactional
    public void delete(UUID id) {
        ChangeRequest changeRequest = findById(id);
        checkAuthorship(changeRequest);
        changeRequestRepository.delete(changeRequest);
    }

    private void checkTaskMembership(ChangeRequest changeRequest) {
        Task task = changeRequest.getTask();
        projectService.checkMembership(task.getProject().getId());
    }

    private void checkIsAssignee(ChangeRequest changeRequest) {
        Task task = changeRequest.getTask();
        projectService.checkMembership(task.getProject().getId());
        if (changeRequest.getRound().getStatus() != TaskReviewRound.RoundStatus.OPEN) {
            throw new InvalidStateException(
                    "Нельзя изменить статус замечания",
                    "раунд ревью уже завершён",
                    "создайте новый раунд ревью при необходимости"
            );
        }
        UUID currentUserId = userService.getCurrentUserId();
        if (!taskAssigneeService.isAssignee(task.getId(), currentUserId)) {
            throw new AccessDeniedException("Изменять статус замечаний может только исполнитель задачи");
        }
    }

    private void checkAuthorship(ChangeRequest changeRequest) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!changeRequest.getAuthor().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Вы можете удалять только свои замечания");
        }
    }
}