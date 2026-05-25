package ru.diploma.studtrack.dto.request;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

public record ProjectStatisticsFilter(
        Period period,
        UUID memberId
) {
    public static ProjectStatisticsFilter of(String rawPeriod, UUID memberId) {
        return new ProjectStatisticsFilter(Period.from(rawPeriod), memberId);
    }

    public enum Period {
        DAYS_7(7, "7d"),
        DAYS_30(30, "30d"),
        DAYS_90(90, "90d"),
        ALL(null, "all");

        private final Integer days;
        private final String code;

        Period(Integer days, String code) {
            this.days = days;
            this.code = code;
        }

        public static Period from(String value) {
            if (value == null || value.isBlank()) {
                return ALL;
            }
            String normalized = value.toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "7d" -> DAYS_7;
                case "30d" -> DAYS_30;
                case "90d" -> DAYS_90;
                case "all" -> ALL;
                default -> ALL;
            };
        }

        public String code() {
            return code;
        }

        public LocalDateTime fromDate(LocalDateTime now) {
            if (days == null) {
                return null;
            }
            return now.minusDays(days);
        }
    }
}
