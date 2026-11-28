package com.bugtrace.service;

import com.bugtrace.config.AppProperties;
import com.bugtrace.dto.response.BulkStatusResponse;
import com.bugtrace.dto.response.IncidentActivityResponse;
import com.bugtrace.dto.response.IncidentDetailResponse;
import com.bugtrace.dto.response.IncidentNoteResponse;
import com.bugtrace.dto.response.IncidentSummaryResponse;
import com.bugtrace.dto.response.LogEntryResponse;
import com.bugtrace.dto.response.PageResponse;
import com.bugtrace.dto.response.RelatedIncidentResponse;
import com.bugtrace.entity.Incident;
import com.bugtrace.entity.IncidentActivity;
import com.bugtrace.entity.IncidentNote;
import com.bugtrace.entity.IncidentTag;
import com.bugtrace.entity.LogEntry;
import com.bugtrace.enums.IncidentActivityType;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import com.bugtrace.exception.InvalidRequestException;
import com.bugtrace.exception.ResourceNotFoundException;
import com.bugtrace.parser.SimilarityScorer;
import com.bugtrace.repository.IncidentActivityRepository;
import com.bugtrace.repository.IncidentNoteRepository;
import com.bugtrace.repository.IncidentRepository;
import com.bugtrace.repository.IncidentSpecifications;
import com.bugtrace.repository.IncidentTagRepository;
import com.bugtrace.repository.LogEntryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Incident search, detail, status changes, tags and investigation notes.
 */
@Service
public class IncidentService {

    private static final int MAX_PAGE_SIZE = 100;
    /** Keeps a single CSV download small enough to stay responsive. */
    private static final int MAX_EXPORT_ROWS = 5_000;
    private static final int RELATED_CANDIDATE_LIMIT = 200;
    private static final int RELATED_RESULT_LIMIT = 5;
    private static final double RELATED_MIN_SCORE = 0.45;
    private static final int MAX_TAGS = 10;
    private static final int MAX_TAG_LENGTH = 32;
    private static final int MAX_ASSIGNEE_LENGTH = 100;
    private static final int MAX_ACTIVITY_DETAIL = 500;
    /** Longest snooze accepted: 30 days. */
    private static final int MAX_SNOOZE_HOURS = 720;
    private static final Pattern TAG_PATTERN =
            Pattern.compile("[a-z0-9][a-z0-9_-]{0," + (MAX_TAG_LENGTH - 1) + "}");

    private final IncidentRepository incidentRepository;
    private final IncidentNoteRepository noteRepository;
    private final LogEntryRepository logEntryRepository;
    private final IncidentTagRepository tagRepository;
    private final IncidentActivityRepository activityRepository;
    private final SimilarityScorer similarityScorer;
    private final ZoneId zoneId;

    public IncidentService(IncidentRepository incidentRepository,
                           IncidentNoteRepository noteRepository,
                           LogEntryRepository logEntryRepository,
                           IncidentTagRepository tagRepository,
                           IncidentActivityRepository activityRepository,
                           SimilarityScorer similarityScorer,
                           AppProperties appProperties) {
        this.incidentRepository = incidentRepository;
        this.noteRepository = noteRepository;
        this.logEntryRepository = logEntryRepository;
        this.tagRepository = tagRepository;
        this.activityRepository = activityRepository;
        this.similarityScorer = similarityScorer;
        try {
            this.zoneId = ZoneId.of(appProperties.getLog().getTimezone());
        } catch (DateTimeException ex) {
            throw new IllegalStateException(
                    "Invalid app.log.timezone: " + appProperties.getLog().getTimezone(), ex);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentSummaryResponse> list(String query, Severity severity,
                                                       IncidentStatus status,
                                                       LocalDate from, LocalDate to,
                                                       String sort, int page, int size) {
        return list(query, severity, status, null, null, null, from, to, sort, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentSummaryResponse> list(String query, Severity severity,
                                                       IncidentStatus status, String tag,
                                                       LocalDate from, LocalDate to,
                                                       String sort, int page, int size) {
        return list(query, severity, status, tag, null, null, from, to, sort, page, size);
    }

    /**
     * @param snoozed {@code TRUE} keeps only snoozed incidents, {@code FALSE}
     *                keeps only active ones, {@code null} keeps both
     */
    @Transactional(readOnly = true)
    public PageResponse<IncidentSummaryResponse> list(String query, Severity severity,
                                                       IncidentStatus status, String tag,
                                                       Boolean snoozed,
                                                       LocalDate from, LocalDate to,
                                                       String sort, int page, int size) {
        return list(query, severity, status, tag, null, snoozed, from, to, sort, page, size);
    }

    /**
     * @param assignee case-insensitive exact owner match, or {@code null} for any owner
     */
    @Transactional(readOnly = true)
    public PageResponse<IncidentSummaryResponse> list(String query, Severity severity,
                                                       IncidentStatus status, String tag,
                                                       String assignee, Boolean snoozed,
                                                       LocalDate from, LocalDate to,
                                                       String sort, int page, int size) {
        validatePaging(page, size);

        Specification<Incident> specification =
                buildSpecification(query, severity, status, tag, assignee, snoozed, from, to);
        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<Incident> incidents = incidentRepository.findAll(specification, pageable);

        Map<Long, List<String>> tags = tagsFor(incidents.getContent());
        return PageResponse.from(incidents, incident -> IncidentSummaryResponse.from(
                incident, tags.getOrDefault(incident.getId(), List.of())));
    }

    /**
     * Exports every incident matching the current filters as CSV.
     *
     * <p>The result is capped at {@value #MAX_EXPORT_ROWS} rows and reports
     * truncation, so a broad filter can never stream an unbounded file.</p>
     */
    @Transactional(readOnly = true)
    public IncidentExport exportCsv(String query, Severity severity, IncidentStatus status,
                                    LocalDate from, LocalDate to, String sort) {
        return exportCsv(query, severity, status, null, null, null, from, to, sort);
    }

    @Transactional(readOnly = true)
    public IncidentExport exportCsv(String query, Severity severity, IncidentStatus status, String tag,
                                    LocalDate from, LocalDate to, String sort) {
        return exportCsv(query, severity, status, tag, null, null, from, to, sort);
    }

    @Transactional(readOnly = true)
    public IncidentExport exportCsv(String query, Severity severity, IncidentStatus status, String tag,
                                    Boolean snoozed,
                                    LocalDate from, LocalDate to, String sort) {
        return exportCsv(query, severity, status, tag, null, snoozed, from, to, sort);
    }

    @Transactional(readOnly = true)
    public IncidentExport exportCsv(String query, Severity severity, IncidentStatus status, String tag,
                                    String assignee, Boolean snoozed,
                                    LocalDate from, LocalDate to, String sort) {
        Specification<Incident> specification =
                buildSpecification(query, severity, status, tag, assignee, snoozed, from, to);

        // Fetch one extra row so truncation can be reported without a count query.
        Pageable pageable = PageRequest.of(0, MAX_EXPORT_ROWS + 1, resolveSort(sort));
        List<Incident> incidents = incidentRepository.findAll(specification, pageable).getContent();

        boolean truncated = incidents.size() > MAX_EXPORT_ROWS;
        if (truncated) {
            incidents = incidents.subList(0, MAX_EXPORT_ROWS);
        }

        byte[] content = IncidentCsvExporter.toCsv(incidents).getBytes(StandardCharsets.UTF_8);
        String filename = "bugtrace-incidents-"
                + LocalDate.now(zoneId).format(DateTimeFormatter.BASIC_ISO_DATE) + ".csv";
        return new IncidentExport(filename, content, incidents.size(), truncated);
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse get(Long id) {
        Incident incident = findIncident(id);
        List<String> tags = tagsFor(incident);
        List<IncidentNoteResponse> notes = noteRepository.findByIncidentIdOrderByCreatedAtAsc(id)
                .stream()
                .map(IncidentNoteResponse::from)
                .toList();
        List<IncidentActivityResponse> activities = activityRepository
                .findByIncidentIdOrderByCreatedAtDescIdDesc(id)
                .stream()
                .map(IncidentActivityResponse::from)
                .toList();
        return IncidentDetailResponse.from(incident, tags, notes, activities, findRelated(incident));
    }

    /**
     * Mute an incident until the given number of hours have passed. Resolved
     * incidents are rejected because there is nothing left to silence.
     */
    @Transactional
    public IncidentSummaryResponse snooze(Long id, Integer hours) {
        if (hours == null || hours < 1 || hours > MAX_SNOOZE_HOURS) {
            throw new InvalidRequestException(
                    "'hours' must be between 1 and " + MAX_SNOOZE_HOURS + ".");
        }
        Incident incident = findIncident(id);
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            throw new InvalidRequestException("Resolved incidents cannot be snoozed.");
        }

        Instant until = Instant.now().plus(Duration.ofHours(hours));
        incident.setSnoozedUntil(until);
        recordActivity(incident.getId(), IncidentActivityType.SNOOZED,
                "Snoozed for " + hours + "h (until " + until + ")");
        return IncidentSummaryResponse.from(incident, tagsFor(incident));
    }

    /** Clear a snooze immediately. Clearing an active incident is a no-op. */
    @Transactional
    public IncidentSummaryResponse unsnooze(Long id) {
        Incident incident = findIncident(id);
        boolean wasSnoozed = incident.getSnoozedUntil() != null
                && incident.getSnoozedUntil().isAfter(Instant.now());
        incident.setSnoozedUntil(null);
        if (wasSnoozed) {
            recordActivity(incident.getId(), IncidentActivityType.UNSNOOZED, "Snooze cleared");
        }
        return IncidentSummaryResponse.from(incident, tagsFor(incident));
    }

    /** Assign an owner (1–100 characters, trimmed). Re-assigning the same owner is a no-op. */
    @Transactional
    public IncidentSummaryResponse assign(Long id, String rawAssignee) {
        String assignee = normalizeAssignee(rawAssignee);
        Incident incident = findIncident(id);
        if (Objects.equals(assignee, incident.getAssignee())) {
            return IncidentSummaryResponse.from(incident, tagsFor(incident));
        }

        String previous = incident.getAssignee();
        incident.setAssignee(assignee);
        recordActivity(incident.getId(), IncidentActivityType.ASSIGNED,
                previous == null ? "Assigned to " + assignee
                        : "Reassigned from " + previous + " to " + assignee);
        return IncidentSummaryResponse.from(incident, tagsFor(incident));
    }

    /** Clear the current owner. Clearing an unassigned incident is a no-op. */
    @Transactional
    public IncidentSummaryResponse unassign(Long id) {
        Incident incident = findIncident(id);
        if (incident.getAssignee() == null) {
            return IncidentSummaryResponse.from(incident, tagsFor(incident));
        }

        String previous = incident.getAssignee();
        incident.setAssignee(null);
        recordActivity(incident.getId(), IncidentActivityType.UNASSIGNED,
                "Unassigned from " + previous);
        return IncidentSummaryResponse.from(incident, tagsFor(incident));
    }

    @Transactional(readOnly = true)
    public List<String> listAssignees() {
        return incidentRepository.findDistinctAssignees();
    }

    @Transactional(readOnly = true)
    public PageResponse<LogEntryResponse> getEntries(Long id, int page, int size) {
        validatePaging(page, size);
        if (!incidentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Incident " + id + " not found");
        }
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("loggedAt"), Sort.Order.desc("id")));
        Page<LogEntry> entries = logEntryRepository.findByIncidentId(id, pageable);
        return PageResponse.from(entries, LogEntryResponse::from);
    }

    @Transactional
    public IncidentSummaryResponse updateStatus(Long id, IncidentStatus status) {
        Incident incident = findIncident(id);
        applyStatus(incident, status);
        return IncidentSummaryResponse.from(incident, tagsFor(incident));
    }

    /**
     * Update several incidents at once. Missing ids fail the whole request so
     * the caller never sees a silently partial result.
     */
    @Transactional
    public BulkStatusResponse bulkUpdateStatus(List<Long> ids, IncidentStatus status) {
        if (ids == null || ids.isEmpty()) {
            throw new InvalidRequestException("'ids' must contain at least one incident id.");
        }
        if (ids.size() > 100) {
            throw new InvalidRequestException("At most 100 incidents can be updated at once.");
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            throw new InvalidRequestException("Incident ids must not be null.");
        }
        if (status == null) {
            throw new InvalidRequestException("'status' is required.");
        }

        List<Long> uniqueIds = ids.stream().distinct().toList();
        List<Incident> incidents = incidentRepository.findAllById(uniqueIds);
        if (incidents.size() != uniqueIds.size()) {
            throw new ResourceNotFoundException("One or more incidents were not found.");
        }

        int updated = 0;
        for (Incident incident : incidents) {
            if (applyStatus(incident, status)) {
                updated++;
            }
        }
        return new BulkStatusResponse(updated, status);
    }

    @Transactional
    public IncidentNoteResponse addNote(Long id, String noteText) {
        if (noteText == null || noteText.isBlank()) {
            throw new InvalidRequestException("noteText must not be blank.");
        }
        Incident incident = findIncident(id);
        String text = noteText.strip();
        IncidentNote note = noteRepository.save(new IncidentNote(incident, text, Instant.now()));
        recordActivity(incident.getId(), IncidentActivityType.NOTE_ADDED,
                "Note added: " + truncate(text, 120));
        return IncidentNoteResponse.from(note);
    }

    /**
     * Replace the complete tag set. Only the difference is written, and every
     * addition/removal is recorded in the activity trail.
     */
    @Transactional
    public List<String> replaceTags(Long id, List<String> rawTags) {
        Incident incident = findIncident(id);
        LinkedHashSet<String> desired = new LinkedHashSet<>();
        if (rawTags != null) {
            for (String rawTag : rawTags) {
                if (rawTag == null || rawTag.isBlank()) {
                    continue;
                }
                desired.add(normalizeTag(rawTag));
            }
        }
        if (desired.size() > MAX_TAGS) {
            throw new InvalidRequestException("An incident can have at most " + MAX_TAGS + " tags.");
        }

        List<IncidentTag> existing = tagRepository.findByIncidentIdOrderByTagAsc(id);
        Set<String> existingTags = existing.stream()
                .map(IncidentTag::getTag)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<IncidentTag> removed = existing.stream()
                .filter(tag -> !desired.contains(tag.getTag()))
                .toList();
        if (!removed.isEmpty()) {
            tagRepository.deleteAll(removed);
            for (IncidentTag tag : removed) {
                recordActivity(incident.getId(), IncidentActivityType.TAG_REMOVED,
                        "Removed tag '" + tag.getTag() + "'");
            }
        }

        List<IncidentTag> added = desired.stream()
                .filter(tag -> !existingTags.contains(tag))
                .map(tag -> new IncidentTag(incident.getId(), tag, Instant.now()))
                .toList();
        if (!added.isEmpty()) {
            tagRepository.saveAll(added);
            for (IncidentTag tag : added) {
                recordActivity(incident.getId(), IncidentActivityType.TAG_ADDED,
                        "Added tag '" + tag.getTag() + "'");
            }
        }

        return desired.stream().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<String> listTags() {
        return tagRepository.findDistinctTags();
    }

    private boolean applyStatus(Incident incident, IncidentStatus status) {
        if (status == null) {
            throw new InvalidRequestException("'status' is required.");
        }
        IncidentStatus previous = incident.getStatus();
        if (previous == status) {
            return false;
        }

        incident.setStatus(status);
        if (status == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(Instant.now());
            // A resolved incident is already out of the active queue.
            incident.setSnoozedUntil(null);
        } else {
            incident.setResolvedAt(null);
        }
        recordActivity(incident.getId(), IncidentActivityType.STATUS_CHANGED,
                previous + " -> " + status);
        return true;
    }

    private void recordActivity(Long incidentId, IncidentActivityType type, String detail) {
        activityRepository.save(new IncidentActivity(
                incidentId, type, truncate(detail, MAX_ACTIVITY_DETAIL), Instant.now()));
    }

    private List<RelatedIncidentResponse> findRelated(Incident incident) {
        List<Incident> candidates = incidentRepository
                .findBySeverityAndIdNotOrderByOccurrenceCountDesc(
                        incident.getSeverity(),
                        incident.getId(),
                        PageRequest.of(0, RELATED_CANDIDATE_LIMIT));

        return candidates.stream()
                .map(candidate -> new AbstractMap.SimpleEntry<>(
                        candidate,
                        similarityScorer.score(incident.getSampleMessage(), candidate.getSampleMessage())))
                .filter(entry -> entry.getValue() >= RELATED_MIN_SCORE)
                .sorted(Comparator.comparingDouble((AbstractMap.SimpleEntry<Incident, Double> entry)
                        -> entry.getValue()).reversed())
                .limit(RELATED_RESULT_LIMIT)
                .map(entry -> RelatedIncidentResponse.from(entry.getKey(), entry.getValue()))
                .toList();
    }

    private Map<Long, List<String>> tagsFor(List<Incident> incidents) {
        if (incidents.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = incidents.stream().map(Incident::getId).toList();
        return tagRepository.findByIncidentIdInOrderByTagAsc(ids).stream()
                .collect(Collectors.groupingBy(
                        IncidentTag::getIncidentId,
                        LinkedHashMap::new,
                        Collectors.mapping(IncidentTag::getTag, Collectors.toList())));
    }

    private List<String> tagsFor(Incident incident) {
        return tagRepository.findByIncidentIdOrderByTagAsc(incident.getId()).stream()
                .map(IncidentTag::getTag)
                .toList();
    }

    private Specification<Incident> buildSpecification(String query, Severity severity,
                                                       IncidentStatus status, String tag,
                                                       String assignee, Boolean snoozed,
                                                       LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be on or before 'to'.");
        }

        Instant fromInstant = from == null ? null : from.atStartOfDay(zoneId).toInstant();
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(zoneId).toInstant();
        String normalizedTag = tag == null || tag.isBlank() ? null : normalizeTag(tag);
        String normalizedAssignee = assignee == null || assignee.isBlank() ? null : assignee.strip();
        return IncidentSpecifications.filter(
                query, severity, status, normalizedTag, normalizedAssignee, snoozed, Instant.now(),
                fromInstant, toExclusive);
    }

    private String normalizeAssignee(String rawAssignee) {
        if (rawAssignee == null || rawAssignee.isBlank()) {
            throw new InvalidRequestException("'assignee' is required.");
        }
        String assignee = rawAssignee.strip();
        if (assignee.length() > MAX_ASSIGNEE_LENGTH) {
            throw new InvalidRequestException(
                    "'assignee' must be at most " + MAX_ASSIGNEE_LENGTH + " characters.");
        }
        return assignee;
    }

    private String normalizeTag(String rawTag) {
        String tag = rawTag.strip().toLowerCase(Locale.ROOT);
        if (!TAG_PATTERN.matcher(tag).matches()) {
            throw new InvalidRequestException(
                    "Invalid tag '" + rawTag + "'. Use 1-" + MAX_TAG_LENGTH
                            + " lowercase letters, digits, '-' or '_'.");
        }
        return tag;
    }

    private Incident findIncident(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident " + id + " not found"));
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank() || sort.equalsIgnoreCase("latest")) {
            return Sort.by(Sort.Order.desc("lastSeen"), Sort.Order.desc("id"));
        }
        if (sort.equalsIgnoreCase("count")) {
            return Sort.by(Sort.Order.desc("occurrenceCount"),
                    Sort.Order.desc("lastSeen"), Sort.Order.desc("id"));
        }
        throw new InvalidRequestException(
                "Invalid sort '" + sort + "'. Use 'latest' or 'count'.");
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("'page' must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("'size' must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
