package ru.diploma.studtrack.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.diploma.studtrack.dto.request.ProjectStatisticsFilter;
import ru.diploma.studtrack.dto.response.ProjectStatisticsResponse;
import ru.diploma.studtrack.model.ChangeRequest;
import ru.diploma.studtrack.model.ProjectMember;
import ru.diploma.studtrack.model.Task;
import ru.diploma.studtrack.model.TaskAssignee;
import ru.diploma.studtrack.model.TaskHistory;
import ru.diploma.studtrack.model.TaskReviewer;
import ru.diploma.studtrack.repository.ChangeRequestRepository;
import ru.diploma.studtrack.repository.TaskHistoryRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/**
 * Рассчитывает агрегированную статистику по проекту и его участникам.
 */
public class ProjectStatisticsService {

    /**
     * Сервис задач.
     */
    private final TaskService taskService;
    /**
     * Сервис проектов.
     */
    private final ProjectService projectService;
    /**
     * Репозиторий истории задач.
     */
    private final TaskHistoryRepository taskHistoryRepository;
    /**
     * Репозиторий замечаний.
     */
    private final ChangeRequestRepository changeRequestRepository;

    /**
     * Собирает статистику проекта с учетом фильтра периода и участника.
     *
     * @param projectId идентификатор проекта
     * @param filter фильтр статистики
     * @return агрегированная статистика проекта
     */
    public ProjectStatisticsResponse getProjectStatistics(UUID projectId, ProjectStatisticsFilter filter) {
        projectService.checkMembership(projectId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime periodStart = filter.period().fromDate(now);

        List<Task> tasks = taskService.getTasksByProject(projectId);
        List<ProjectMember> members = projectService.getMembers(projectId);
        Map<UUID, List<TaskHistory>> statusHistoryByTask = loadStatusHistoryByTask(projectId, periodStart);
        Map<UUID, DurationStats> doneDurationStatsByTask = buildDurationStatsByTask(tasks, statusHistoryByTask);

        ProjectStatisticsResponse.ProjectKpi projectKpi = buildProjectKpi(tasks, periodStart);
        ProjectStatisticsResponse.DurationKpi durationKpi = buildDurationKpi(tasks, doneDurationStatsByTask, now);
        List<ProjectStatisticsResponse.MemberStats> memberStats = buildMemberStats(
                tasks,
                members,
                doneDurationStatsByTask,
                periodStart,
                filter.memberId()
        );

        return new ProjectStatisticsResponse(
                filter.period(),
                filter.memberId(),
                now,
                projectKpi,
                durationKpi,
                memberStats
        );
    }

    /**
     * Рассчитывает KPI проекта по задачам и срокам.
     *
     * @param tasks список задач проекта
     * @param periodStart начало периода фильтра
     * @return KPI проекта
     */
    private ProjectStatisticsResponse.ProjectKpi buildProjectKpi(List<Task> tasks, LocalDateTime periodStart) {
        long done = tasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.DONE).count();
        long inProgress = tasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.IN_PROGRESS).count();
        long review = tasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.REVIEW).count();
        long backlog = tasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.BACKLOG).count();
        long todo = tasks.stream().filter(t -> t.getStatus() == Task.TaskStatus.TODO).count();
        long open = tasks.size() - done;
        LocalDate today = LocalDate.now();
        long overdue = tasks.stream()
                .filter(t -> t.getStatus() != Task.TaskStatus.DONE)
                .filter(t -> t.getDeadline() != null && t.getDeadline().isBefore(today))
                .count();
        long reviewRequired = tasks.stream().filter(Task::isReviewRequired).count();
        long createdInPeriod = periodStart == null ? tasks.size() :
                tasks.stream().filter(t -> t.getCreatedAt() != null && !t.getCreatedAt().isBefore(periodStart)).count();

        return new ProjectStatisticsResponse.ProjectKpi(
                tasks.size(),
                done,
                inProgress,
                review,
                backlog,
                todo,
                overdue,
                reviewRequired,
                open,
                createdInPeriod,
                done
        );
    }

    /**
     * Рассчитывает KPI по длительностям lead/cycle time.
     *
     * @param tasks список задач проекта
     * @param doneDurationStatsByTask длительности по завершённым задачам
     * @param now текущий момент времени
     * @return KPI длительностей
     */
    private ProjectStatisticsResponse.DurationKpi buildDurationKpi(
            List<Task> tasks,
            Map<UUID, DurationStats> doneDurationStatsByTask,
            LocalDateTime now
    ) {
        List<Double> lead = new ArrayList<>();
        List<Double> cycle = new ArrayList<>();
        for (Task task : tasks) {
            DurationStats stats = doneDurationStatsByTask.get(task.getId());
            if (stats == null) {
                continue;
            }
            if (stats.leadHours != null) {
                lead.add(stats.leadHours);
            }
            if (stats.cycleHours != null) {
                cycle.add(stats.cycleHours);
            }
        }

        List<Double> openAge = tasks.stream()
                .filter(t -> t.getStatus() != Task.TaskStatus.DONE)
                .filter(t -> t.getCreatedAt() != null)
                .map(t -> Duration.between(t.getCreatedAt(), now).toHours() / 1.0d)
                .toList();

        return new ProjectStatisticsResponse.DurationKpi(
                lead.size(),
                cycle.size(),
                average(lead),
                minimum(lead),
                maximum(lead),
                average(cycle),
                minimum(cycle),
                maximum(cycle),
                average(openAge)
        );
    }

    /**
     * Формирует статистику по каждому участнику проекта.
     *
     * @param tasks задачи проекта
     * @param members участники проекта
     * @param durationByTaskId длительности по задачам
     * @param periodStart начало периода
     * @param selectedMemberId идентификатор выбранного участника
     * @return список статистики по участникам
     */
    private List<ProjectStatisticsResponse.MemberStats> buildMemberStats(
            List<Task> tasks,
            List<ProjectMember> members,
            Map<UUID, DurationStats> durationByTaskId,
            LocalDateTime periodStart,
            UUID selectedMemberId
    ) {
        Map<UUID, List<Task>> tasksByAssignee = new LinkedHashMap<>();
        for (Task task : tasks) {
            for (TaskAssignee assignee : task.getAssignees()) {
                tasksByAssignee.computeIfAbsent(assignee.getUser().getId(), ignored -> new ArrayList<>()).add(task);
            }
        }

        Map<UUID, List<TaskReviewer>> reviewerAssignments = new HashMap<>();
        for (Task task : tasks) {
            for (TaskReviewer reviewer : task.getReviewers()) {
                reviewerAssignments.computeIfAbsent(reviewer.getUser().getId(), ignored -> new ArrayList<>()).add(reviewer);
            }
        }

        List<ProjectStatisticsResponse.MemberStats> result = new ArrayList<>();
        for (ProjectMember member : members) {
            UUID userId = member.getUser().getId();
            if (selectedMemberId != null && !selectedMemberId.equals(userId)) {
                continue;
            }
            List<Task> assigneeTasks = tasksByAssignee.getOrDefault(userId, List.of());
            List<TaskReviewer> reviews = reviewerAssignments.getOrDefault(userId, List.of());

            long assignedOpenNow = assigneeTasks.stream().filter(t -> t.getStatus() != Task.TaskStatus.DONE).count();
            long doneInPeriod = assigneeTasks.stream()
                    .filter(t -> t.getStatus() == Task.TaskStatus.DONE)
                    .filter(t -> periodStart == null || (t.getUpdatedAt() != null && !t.getUpdatedAt().isBefore(periodStart)))
                    .count();
            long reviewPendingNow = reviews.stream()
                    .filter(r -> r.getStatus() == TaskReviewer.ReviewStatus.PENDING)
                    .count();
            long reviewTotalAllTime = reviews.size();

            long openCrInOwnTasks = assigneeTasks.stream()
                    .map(Task::getId)
                    .distinct()
                    .mapToLong(taskId -> changeRequestRepository.findByTaskIdAndStatus(taskId, ChangeRequest.ChangeRequestStatus.OPEN).size())
                    .sum();

            List<Double> memberLead = new ArrayList<>();
            List<Double> memberCycle = new ArrayList<>();
            for (Task task : assigneeTasks) {
                DurationStats stats = durationByTaskId.get(task.getId());
                if (stats == null) {
                    continue;
                }
                if (stats.leadHours != null) {
                    memberLead.add(stats.leadHours);
                }
                if (stats.cycleHours != null) {
                    memberCycle.add(stats.cycleHours);
                }
            }

            result.add(new ProjectStatisticsResponse.MemberStats(
                    userId,
                    member.getUser().getFullName(),
                    assignedOpenNow,
                    reviewPendingNow,
                    reviewTotalAllTime,
                    doneInPeriod,
                    openCrInOwnTasks,
                    memberLead.size(),
                    memberCycle.size(),
                    average(memberLead),
                    minimum(memberLead),
                    maximum(memberLead),
                    average(memberCycle),
                    minimum(memberCycle),
                    maximum(memberCycle)
            ));
        }

        return result.stream()
                .sorted(Comparator.comparing(ProjectStatisticsResponse.MemberStats::fullName, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    /**
     * Загружает историю смены статусов задач проекта.
     *
     * @param projectId идентификатор проекта
     * @param periodStart начало периода
     * @return карта taskId -> список событий смены статуса
     */
    private Map<UUID, List<TaskHistory>> loadStatusHistoryByTask(UUID projectId, LocalDateTime periodStart) {
        List<TaskHistory> history = periodStart == null
                ? taskHistoryRepository.findStatusHistoryByProject(projectId, TaskHistory.EventType.TASK_STATUS_CHANGED)
                : taskHistoryRepository.findStatusHistoryByProjectAndCreatedAtAfter(
                        projectId,
                        TaskHistory.EventType.TASK_STATUS_CHANGED,
                        periodStart
                );
        return history.stream()
                .collect(Collectors.groupingBy(
                        h -> h.getTask().getId(),
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), list -> list.stream()
                                .sorted(Comparator.comparing(TaskHistory::getCreatedAt))
                                .toList())
                ));
    }

    /**
     * Строит длительности lead/cycle по задачам на основе истории статусов.
     *
     * @param tasks задачи проекта
     * @param statusHistoryByTask история статусов по задачам
     * @return карта taskId -> рассчитанные длительности
     */
    private Map<UUID, DurationStats> buildDurationStatsByTask(List<Task> tasks, Map<UUID, List<TaskHistory>> statusHistoryByTask) {
        Map<UUID, DurationStats> result = new HashMap<>();
        for (Task task : tasks) {
            List<TaskHistory> statusHistory = statusHistoryByTask.getOrDefault(task.getId(), List.of());
            LocalDateTime firstDoneAt = firstStatusAt(statusHistory, "DONE");
            if (firstDoneAt == null || task.getCreatedAt() == null) {
                continue;
            }
            LocalDateTime firstInProgressAt = firstStatusAt(statusHistory, "IN_PROGRESS");
            Double leadHours = durationHours(task.getCreatedAt(), firstDoneAt);
            Double cycleHours = firstInProgressAt == null ? null : durationHours(firstInProgressAt, firstDoneAt);
            result.put(task.getId(), new DurationStats(leadHours, cycleHours));
        }
        return result;
    }

    /**
     * Находит первое событие перехода в указанный статус.
     *
     * @param statusEvents события статусов
     * @param status целевой статус
     * @return дата перехода или null
     */
    private LocalDateTime firstStatusAt(List<TaskHistory> statusEvents, String status) {
        for (TaskHistory event : statusEvents) {
            if (event.getDetailsJson() == null) {
                continue;
            }
            String newStatus = extractJsonValue(event.getDetailsJson(), "newStatus");
            if (status.equals(newStatus)) {
                return event.getCreatedAt();
            }
        }
        return null;
    }

    /**
     * Извлекает строковое значение по ключу из простого JSON.
     *
     * @param json JSON-строка
     * @param key ключ
     * @return извлеченное значение или null
     */
    private String extractJsonValue(String json, String key) {
        String token = "\"" + key + "\":";
        int start = json.indexOf(token);
        if (start < 0) {
            return null;
        }
        int valueStart = start + token.length();
        if (valueStart >= json.length() || json.charAt(valueStart) != '"') {
            return null;
        }
        int end = json.indexOf('"', valueStart + 1);
        if (end < 0) {
            return null;
        }
        return json.substring(valueStart + 1, end);
    }

    /**
     * Возвращает длительность между датами в часах.
     *
     * @param start начало интервала
     * @param end конец интервала
     * @return длительность в часах или null
     */
    private Double durationHours(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        return Duration.between(start, end).toHours() / 1.0d;
    }

    /**
     * Возвращает среднее значение списка.
     *
     * @param values значения
     * @return среднее или null
     */
    private Double average(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0d);
    }

    /**
     * Возвращает минимальное значение списка.
     *
     * @param values значения
     * @return минимум или null
     */
    private Double minimum(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).min().orElse(0d);
    }

    /**
     * Возвращает максимальное значение списка.
     *
     * @param values значения
     * @return максимум или null
     */
    private Double maximum(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.stream().mapToDouble(Double::doubleValue).max().orElse(0d);
    }

    /**
     * Хранит рассчитанные длительности для задачи.
     *
     * @param leadHours lead time в часах
     * @param cycleHours cycle time в часах
     */
    private record DurationStats(Double leadHours, Double cycleHours) {
    }
}
