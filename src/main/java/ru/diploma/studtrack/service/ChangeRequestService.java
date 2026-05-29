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
/**
 * Управляет жизненным циклом замечаний по задачам в раундах ревью.
 */
public class ChangeRequestService {

    /**
     * Репозиторий замечаний.
     */
    private final ChangeRequestRepository changeRequestRepository;
    /**
     * Сервис работы с раундами ревью.
     */
    private final TaskReviewRoundService roundService;
    /**
     * Сервис работы с задачами.
     */
    private final TaskService taskService;
    /**
     * Сервис работы с исполнителями.
     */
    private final TaskAssigneeService taskAssigneeService;
    /**
     * Сервис работы с пользователями.
     */
    private final UserService userService;
    /**
     * Сервис проверки доступа к проекту.
     */
    private final ProjectService projectService;
    /**
     * Сервис отправки уведомлений.
     */
    private final NotificationService notificationService;

    /**
     * Возвращает замечания задачи.
     *
     * @param taskId идентификатор задачи
     * @return список замечаний
     */
    public List<ChangeRequest> getByTask(UUID taskId) {
        return changeRequestRepository.findByTaskId(taskId);
    }

    /**
     * Возвращает замечания конкретного раунда ревью.
     *
     * @param roundId идентификатор раунда
     * @return список замечаний
     */
    public List<ChangeRequest> getByRound(UUID roundId) {
        return changeRequestRepository.findByRoundId(roundId);
    }

    /**
     * Возвращает открытые замечания раунда ревью.
     *
     * @param roundId идентификатор раунда
     * @return список открытых замечаний
     */
    public List<ChangeRequest> getOpenByRound(UUID roundId) {
        return changeRequestRepository.findByRoundIdAndStatus(roundId, ChangeRequest.ChangeRequestStatus.OPEN);
    }

    /**
     * Возвращает замечание по идентификатору.
     *
     * @param id идентификатор замечания
     * @return замечание
     */
    public ChangeRequest findById(UUID id) {
        return changeRequestRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Замечание", id));
    }

    @Transactional
    /**
     * Создаёт новое замечание в раунде ревью задачи.
     *
     * @param taskId идентификатор задачи
     * @param roundId идентификатор раунда
     * @param content текст замечания
     * @return созданное замечание
     */
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
    /**
     * Обновляет текст замечания.
     *
     * @param id идентификатор замечания
     * @param content новый текст
     * @return обновлённое замечание
     */
    public ChangeRequest updateContent(UUID id, String content) {
        ChangeRequest changeRequest = findById(id);
        checkTaskMembership(changeRequest);
        changeRequest.setContent(content);
        return changeRequestRepository.save(changeRequest);
    }

    @Transactional
    /**
     * Переводит замечание в статус исправленного.
     *
     * @param id идентификатор замечания
     * @return обновлённое замечание
     */
    public ChangeRequest markAsResolved(UUID id) {
        ChangeRequest changeRequest = findById(id);
        checkIsAssignee(changeRequest);
        changeRequest.setStatus(ChangeRequest.ChangeRequestStatus.RESOLVED);
        return changeRequestRepository.save(changeRequest);
    }

    @Transactional
    /**
     * Переводит замечание в статус отклонённого.
     *
     * @param id идентификатор замечания
     * @return обновлённое замечание
     */
    public ChangeRequest markAsRejected(UUID id) {
        ChangeRequest changeRequest = findById(id);
        checkIsAssignee(changeRequest);
        changeRequest.setStatus(ChangeRequest.ChangeRequestStatus.REJECTED);
        return changeRequestRepository.save(changeRequest);
    }

    @Transactional
    /**
     * Переоткрывает замечание.
     *
     * @param id идентификатор замечания
     * @return обновлённое замечание
     */
    public ChangeRequest markAsOpen(UUID id) {
        ChangeRequest changeRequest = findById(id);
        checkIsAssignee(changeRequest);
        changeRequest.setStatus(ChangeRequest.ChangeRequestStatus.OPEN);
        return changeRequestRepository.save(changeRequest);
    }

    @Transactional
    /**
     * Удаляет замечание.
     *
     * @param id идентификатор замечания
     */
    public void delete(UUID id) {
        ChangeRequest changeRequest = findById(id);
        checkAuthorship(changeRequest);
        changeRequestRepository.delete(changeRequest);
    }

    /**
     * Проверяет членство пользователя в проекте задачи замечания.
     *
     * @param changeRequest замечание
     */
    private void checkTaskMembership(ChangeRequest changeRequest) {
        Task task = changeRequest.getTask();
        projectService.checkMembership(task.getProject().getId());
    }

    /**
     * Проверяет, что действие выполняет исполнитель задачи и раунд ещё открыт.
     *
     * @param changeRequest замечание
     */
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

    /**
     * Проверяет, что текущий пользователь является автором замечания.
     *
     * @param changeRequest замечание
     */
    private void checkAuthorship(ChangeRequest changeRequest) {
        UUID currentUserId = userService.getCurrentUserId();
        if (!changeRequest.getAuthor().getId().equals(currentUserId)) {
            throw new AccessDeniedException("Вы можете удалять только свои замечания");
        }
    }
}