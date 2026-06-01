package ru.diploma.studtrack.dto.response;

import ru.diploma.studtrack.dto.request.ProjectStatisticsFilter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO ответа с агрегированной статистикой проекта.
 */
public record ProjectStatisticsResponse(
        ProjectStatisticsFilter.Period period,
        UUID selectedMemberId,
        LocalDateTime generatedAt,
        ProjectKpi kpi,
        DurationKpi duration,
        List<MemberStats> members
) {
    /**
     * Группа метрик проекта по статусам задач и выполнению.
     */
    public record ProjectKpi(
            long totalTasks,
            long doneTasks,
            long inProgressTasks,
            long reviewTasks,
            long backlogTasks,
            long todoTasks,
            long overdueTasks,
            long reviewRequiredTasks,
            long openTasks,
            long createdInPeriod,
            long doneInPeriod
    ) {
        public double completionPercent() {
            if (totalTasks == 0) {
                return 0d;
            }
            return (doneTasks * 100.0d) / totalTasks;
        }

        public double reviewRequiredPercent() {
            if (totalTasks == 0) {
                return 0d;
            }
            return (reviewRequiredTasks * 100.0d) / totalTasks;
        }
    }

    /**
     * Метрики длительности задач: lead time, cycle time и возраст открытых задач.
     */
    public record DurationKpi(
            long leadSampleSize,
            long cycleSampleSize,
            Double avgLeadHours,
            Double minLeadHours,
            Double maxLeadHours,
            Double avgCycleHours,
            Double minCycleHours,
            Double maxCycleHours,
            Double avgOpenAgeHours
    ) {
    }

    /**
     * Персональные метрики участника проекта.
     */
    public record MemberStats(
            UUID userId,
            String fullName,
            long assignedOpenNow,
            long reviewPendingNow,
            long reviewTotalAllTime,
            long doneInPeriod,
            long openChangeRequestsInOwnTasks,
            long leadSampleSize,
            long cycleSampleSize,
            Double avgLeadHours,
            Double minLeadHours,
            Double maxLeadHours,
            Double avgCycleHours,
            Double minCycleHours,
            Double maxCycleHours
    ) {
    }
}
