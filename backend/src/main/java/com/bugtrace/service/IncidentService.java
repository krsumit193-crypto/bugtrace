package com.bugtrace.service;

import com.bugtrace.config.AppProperties;
import com.bugtrace.dto.response.IncidentDetailResponse;
import com.bugtrace.dto.response.IncidentNoteResponse;
import com.bugtrace.dto.response.IncidentSummaryResponse;
import com.bugtrace.dto.response.LogEntryResponse;
import com.bugtrace.dto.response.PageResponse;
import com.bugtrace.dto.response.RelatedIncidentResponse;
import com.bugtrace.entity.Incident;
import com.bugtrace.entity.IncidentNote;
import com.bugtrace.entity.LogEntry;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import com.bugtrace.exception.InvalidRequestException;
import com.bugtrace.exception.ResourceNotFoundException;
import com.bugtrace.parser.SimilarityScorer;
import com.bugtrace.repository.IncidentNoteRepository;
import com.bugtrace.repository.IncidentRepository;
import com.bugtrace.repository.IncidentSpecifications;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.AbstractMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Incident search, detail, status changes and investigation notes.
 */
@Service
public class IncidentService {

    private static final int MAX_PAGE_SIZE = 100;
    /** Keeps a single CSV download small enough to stay responsive. */
    private static final int MAX_EXPORT_ROWS = 5_000;
    private static final int RELATED_CANDIDATE_LIMIT = 200;
    private static final int RELATED_RESULT_LIMIT = 5;
    private static final double RELATED_MIN_SCORE = 0.45;

    private final IncidentRepository incidentRepository;
    private final IncidentNoteRepository noteRepository;
    private final LogEntryRepository logEntryRepository;
    private final SimilarityScorer similarityScorer;
    private final ZoneId zoneId;

    public IncidentService(IncidentRepository incidentRepository,
                           IncidentNoteRepository noteRepository,
                           LogEntryRepository logEntryRepository,
                           SimilarityScorer similarityScorer,
                           AppProperties appProperties) {
        this.incidentRepository = incidentRepository;
        this.noteRepository = noteRepository;
        this.logEntryRepository = logEntryRepository;
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
        validatePaging(page, size);

        Specification<Incident> specification = buildSpecification(query, severity, status, from, to);

        Pageable pageable = PageRequest.of(page, size, resolveSort(sort));
        Page<Incident> incidents = incidentRepository.findAll(specification, pageable);
        return PageResponse.from(incidents, IncidentSummaryResponse::from);
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
        Specification<Incident> specification = buildSpecification(query, severity, status, from, to);

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
        List<IncidentNoteResponse> notes = noteRepository.findByIncidentIdOrderByCreatedAtAsc(id)
                .stream()
                .map(IncidentNoteResponse::from)
                .toList();
        return IncidentDetailResponse.from(incident, notes, findRelated(incident));
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
        incident.setStatus(status);
        return IncidentSummaryResponse.from(incident);
    }

    @Transactional
    public IncidentNoteResponse addNote(Long id, String noteText) {
        if (noteText == null || noteText.isBlank()) {
            throw new InvalidRequestException("noteText must not be blank.");
        }
        Incident incident = findIncident(id);
        IncidentNote note = noteRepository.save(new IncidentNote(incident, noteText.strip(), Instant.now()));
        return IncidentNoteResponse.from(note);
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

    private Specification<Incident> buildSpecification(String query, Severity severity,
                                                       IncidentStatus status,
                                                       LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidRequestException("'from' must be on or before 'to'.");
        }

        Instant fromInstant = from == null ? null : from.atStartOfDay(zoneId).toInstant();
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(zoneId).toInstant();
        return IncidentSpecifications.filter(query, severity, status, fromInstant, toExclusive);
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
}
