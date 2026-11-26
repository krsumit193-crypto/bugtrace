package com.bugtrace.service;

import com.bugtrace.config.AppProperties;
import com.bugtrace.dto.response.DashboardStatsResponse;
import com.bugtrace.dto.response.IncidentSummaryResponse;
import com.bugtrace.dto.response.ServiceErrorCountResponse;
import com.bugtrace.dto.response.SeverityCountResponse;
import com.bugtrace.dto.response.TrendPointResponse;
import com.bugtrace.entity.Incident;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import com.bugtrace.repository.IncidentRepository;
import com.bugtrace.repository.LogEntryRepository;
import com.bugtrace.repository.LogUploadRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds the overview dashboard from database aggregates. No values are
 * hardcoded; an empty database returns zeros and empty lists.
 */
@Service
public class DashboardService {

    private static final int TOP_LIMIT = 5;
    private static final int SERVICE_LIMIT = 8;
    private static final int MAX_TREND_DAYS = 90;

    private final LogUploadRepository uploadRepository;
    private final LogEntryRepository logEntryRepository;
    private final IncidentRepository incidentRepository;
    private final AppProperties appProperties;

    public DashboardService(LogUploadRepository uploadRepository,
                            LogEntryRepository logEntryRepository,
                            IncidentRepository incidentRepository,
                            AppProperties appProperties) {
        this.uploadRepository = uploadRepository;
        this.logEntryRepository = logEntryRepository;
        this.incidentRepository = incidentRepository;
        this.appProperties = appProperties;
    }

    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        return getStats(appProperties.getDashboard().getTrendDays());
    }

    /**
     * @param requestedDays trend/range window, clamped to 1..90 days
     */
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats(int requestedDays) {
        int days = Math.max(1, Math.min(requestedDays, MAX_TREND_DAYS));

        LogUploadRepository.UploadTotals totals = uploadRepository.summaryTotals();

        long totalUploads = valueOrZero(totals.getUploadCount());
        long totalLines = valueOrZero(totals.getTotalLines());
        long parsedEntries = valueOrZero(totals.getParsedEntries());
        long malformedEntries = valueOrZero(totals.getMalformedEntries());
        long totalEntries = logEntryRepository.count();
        long uniqueIncidents = incidentRepository.count();
        long openIncidents = incidentRepository.countByStatus(IncidentStatus.OPEN);
        long snoozedIncidents = incidentRepository.countBySnoozedUntilAfter(Instant.now());

        long warnEntries = logEntryRepository.countBySeverity(Severity.WARN);
        long errorEntries = logEntryRepository.countBySeverity(Severity.ERROR);
        long fatalEntries = logEntryRepository.countBySeverity(Severity.FATAL);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant rangeStart = today.minusDays(days - 1L).atStartOfDay(ZoneOffset.UTC).toInstant();
        long incidentsInRange = incidentRepository.countByLastSeenGreaterThanEqual(rangeStart);
        List<Incident> resolvedInRange = incidentRepository
                .findByStatusAndResolvedAtGreaterThanEqual(IncidentStatus.RESOLVED, rangeStart);
        Long mttrMinutes = averageResolutionMinutes(resolvedInRange);

        return new DashboardStatsResponse(
                totalUploads,
                totalLines,
                parsedEntries,
                malformedEntries,
                totalEntries,
                uniqueIncidents,
                openIncidents,
                snoozedIncidents,
                warnEntries,
                errorEntries,
                fatalEntries,
                days,
                incidentsInRange,
                resolvedInRange.size(),
                mttrMinutes,
                topRepeatedErrors(),
                errorsByService(),
                severityDistribution(),
                errorsOverTime(days),
                recentIncidents()
        );
    }

    private List<IncidentSummaryResponse> topRepeatedErrors() {
        return incidentRepository.findAllByOrderByOccurrenceCountDesc(PageRequest.of(0, TOP_LIMIT))
                .stream()
                .map(IncidentSummaryResponse::from)
                .toList();
    }

    private List<IncidentSummaryResponse> recentIncidents() {
        return incidentRepository.findAllByOrderByLastSeenDesc(PageRequest.of(0, TOP_LIMIT))
                .stream()
                .map(IncidentSummaryResponse::from)
                .toList();
    }

    private List<ServiceErrorCountResponse> errorsByService() {
        return logEntryRepository.countByService(
                        Set.of(Severity.ERROR, Severity.FATAL),
                        PageRequest.of(0, SERVICE_LIMIT))
                .stream()
                .map(row -> new ServiceErrorCountResponse(
                        row[0] == null ? "unknown" : String.valueOf(row[0]),
                        ((Number) row[1]).longValue()))
                .toList();
    }

    private List<SeverityCountResponse> severityDistribution() {
        Map<Severity, Long> counts = new EnumMap<>(Severity.class);
        long unknown = 0;

        for (Object[] row : logEntryRepository.countGroupedBySeverity()) {
            Severity severity = (Severity) row[0];
            long count = ((Number) row[1]).longValue();
            if (severity == null) {
                unknown += count;
            } else {
                counts.put(severity, count);
            }
        }

        List<SeverityCountResponse> distribution = new ArrayList<>();
        for (Severity severity : Severity.values()) {
            distribution.add(new SeverityCountResponse(
                    severity.name(), counts.getOrDefault(severity, 0L)));
        }
        if (unknown > 0) {
            distribution.add(new SeverityCountResponse("UNKNOWN", unknown));
        }
        return distribution;
    }

    private List<TrendPointResponse> errorsOverTime(int days) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = today.minusDays(days - 1L);
        Instant from = start.atStartOfDay(ZoneOffset.UTC).toInstant();

        List<Object[]> rows = logEntryRepository.countDaily(
                List.of(Severity.ERROR.name(), Severity.FATAL.name()), from);

        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date = toLocalDate(row[0]);
            if (date != null) {
                byDay.put(date, ((Number) row[1]).longValue());
            }
        }

        List<TrendPointResponse> points = new ArrayList<>(days);
        for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
            points.add(new TrendPointResponse(date, byDay.getOrDefault(date, 0L)));
        }
        return points;
    }

    /**
     * Mean time from first observation to resolution, in minutes, for the
     * incidents resolved inside the selected range. Returns {@code null} when
     * nothing was resolved in that window (the UI shows "—").
     */
    private Long averageResolutionMinutes(List<Incident> resolved) {
        if (resolved.isEmpty()) {
            return null;
        }

        long totalMinutes = 0;
        int counted = 0;
        for (Incident incident : resolved) {
            if (incident.getFirstSeen() == null || incident.getResolvedAt() == null) {
                continue;
            }
            long minutes = Duration.between(incident.getFirstSeen(), incident.getResolvedAt()).toMinutes();
            if (minutes >= 0) {
                totalMinutes += minutes;
                counted++;
            }
        }
        return counted == 0 ? null : Math.round((double) totalMinutes / counted);
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date utilDate) {
            return utilDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate();
        }
        return null;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }
}
