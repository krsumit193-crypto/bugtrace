package com.bugtrace.service;

import com.bugtrace.config.AppProperties;
import com.bugtrace.dto.response.LogEntryResponse;
import com.bugtrace.dto.response.PageResponse;
import com.bugtrace.enums.Severity;
import com.bugtrace.exception.InvalidRequestException;
import com.bugtrace.repository.LogEntryRepository;
import com.bugtrace.repository.LogEntrySpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Searches raw log entries from every upload, independently of incidents.
 *
 * <p>This complements incident search: an incident groups repeated failures,
 * while this service finds the exact lines (including INFO/DEBUG lines that
 * never become incidents).</p>
 */
@Service
public class LogEntrySearchService {

    private static final int MAX_PAGE_SIZE = 100;

    private final LogEntryRepository logEntryRepository;
    private final ZoneId zoneId;

    public LogEntrySearchService(LogEntryRepository logEntryRepository,
                                 AppProperties appProperties) {
        this.logEntryRepository = logEntryRepository;
        try {
            this.zoneId = ZoneId.of(appProperties.getLog().getTimezone());
        } catch (DateTimeException ex) {
            throw new IllegalStateException(
                    "Invalid app.log.timezone: " + appProperties.getLog().getTimezone(), ex);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<LogEntryResponse> search(String query, Severity severity,
                                                 String serviceName, Long uploadId,
                                                 Long incidentId,
                                                 LocalDate from, LocalDate to,
                                                 String sort, int page, int size) {
        validatePaging(page, size);
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be on or before 'to'.");
        }

        Instant fromInstant = from == null ? null : from.atStartOfDay(zoneId).toInstant();
        Instant toExclusive = to == null
                ? null
                : to.plusDays(1).atStartOfDay(zoneId).toInstant();

        Specification<com.bugtrace.entity.LogEntry> specification = LogEntrySpecifications.search(
                query, severity, serviceName, uploadId, incidentId, fromInstant, toExclusive);

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<com.bugtrace.entity.LogEntry> entries =
                logEntryRepository.findAll(specification, pageable);
        return PageResponse.from(entries, LogEntryResponse::from);
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("latest")) {
            return Sort.by(Sort.Order.desc("loggedAt"), Sort.Order.desc("id"));
        }
        if (sort.equalsIgnoreCase("oldest")) {
            return Sort.by(Sort.Order.asc("loggedAt"), Sort.Order.asc("id"));
        }
        if (sort.equalsIgnoreCase("line")) {
            return Sort.by(Sort.Order.asc("lineNumber"), Sort.Order.asc("id"));
        }
        throw new InvalidRequestException(
                "Invalid sort '" + sort + "'. Use 'latest', 'oldest' or 'line'.");
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("'page' must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "'size' must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }
}
