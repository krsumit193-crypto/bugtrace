package com.bugtrace.service;

import com.bugtrace.config.AppProperties;
import com.bugtrace.dto.response.IncidentSummaryResponse;
import com.bugtrace.dto.response.PageResponse;
import com.bugtrace.dto.response.UploadDetailResponse;
import com.bugtrace.dto.response.UploadResponse;
import com.bugtrace.entity.Incident;
import com.bugtrace.entity.LogEntry;
import com.bugtrace.entity.LogUpload;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import com.bugtrace.enums.UploadStatus;
import com.bugtrace.exception.FileTooLargeException;
import com.bugtrace.exception.InvalidUploadException;
import com.bugtrace.exception.ResourceNotFoundException;
import com.bugtrace.exception.UnsupportedFileTypeException;
import com.bugtrace.exception.UploadProcessingException;
import com.bugtrace.parser.LogLineParser;
import com.bugtrace.parser.ParseResult;
import com.bugtrace.parser.ParsedLogLine;
import com.bugtrace.repository.IncidentRepository;
import com.bugtrace.repository.LogEntryRepository;
import com.bugtrace.repository.LogUploadRepository;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates, parses and persists an uploaded log file.
 *
 * <p>Processing is synchronous. The upload row is created after the file has
 * been validated and parsed, so a rejected file does not leave a stray
 * PROCESSING row. A persistence failure marks the upload FAILED.</p>
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private static final int CHUNK_SIZE = 1000;
    private static final int MAX_FILENAME_LENGTH = 255;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_UPLOAD_INCIDENTS = 100;

    private final LogUploadRepository uploadRepository;
    private final LogEntryRepository logEntryRepository;
    private final IncidentRepository incidentRepository;
    private final LogLineParser logLineParser;
    private final IncidentGrouper incidentGrouper;
    private final AppProperties appProperties;
    private final EntityManager entityManager;
    private final TransactionTemplate transactionTemplate;
    private final ZoneId zoneId;

    public UploadService(LogUploadRepository uploadRepository,
                         LogEntryRepository logEntryRepository,
                         IncidentRepository incidentRepository,
                         LogLineParser logLineParser,
                         IncidentGrouper incidentGrouper,
                         AppProperties appProperties,
                         EntityManager entityManager,
                         PlatformTransactionManager transactionManager) {
        this.uploadRepository = uploadRepository;
        this.logEntryRepository = logEntryRepository;
        this.incidentRepository = incidentRepository;
        this.logLineParser = logLineParser;
        this.incidentGrouper = incidentGrouper;
        this.appProperties = appProperties;
        this.entityManager = entityManager;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        try {
            this.zoneId = ZoneId.of(appProperties.getLog().getTimezone());
        } catch (DateTimeException ex) {
            throw new IllegalStateException(
                    "Invalid app.log.timezone: " + appProperties.getLog().getTimezone(), ex);
        }
    }

    public UploadResponse upload(MultipartFile file) {
        String filename = validateAndSanitize(file);

        ParseResult parseResult;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            parseResult = logLineParser.parse(reader, zoneId);
        } catch (IOException ex) {
            throw new InvalidUploadException("The uploaded file could not be read.");
        }

        LogUpload upload = uploadRepository.save(new LogUpload(
                UUID.randomUUID().toString(),
                filename,
                file.getSize(),
                UploadStatus.PROCESSING,
                Instant.now()
        ));

        try {
            persist(upload.getId(), parseResult, upload.getUploadedAt());
            LogUpload completed = uploadRepository.findById(upload.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Upload " + upload.getId() + " not found"));
            return UploadResponse.from(completed);
        } catch (RuntimeException ex) {
            markFailed(upload.getId(), ex);
            throw new UploadProcessingException(
                    "The upload was saved but could not be processed: " + rootMessage(ex), ex);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<UploadResponse> list(int page, int size) {
        validatePaging(page, size);
        Page<LogUpload> uploads = uploadRepository.findAll(PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("uploadedAt"), Sort.Order.desc("id"))));
        return PageResponse.from(uploads, UploadResponse::from);
    }

    @Transactional(readOnly = true)
    public UploadDetailResponse get(Long id) {
        LogUpload upload = uploadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Upload " + id + " not found"));
        List<IncidentSummaryResponse> incidents = logEntryRepository
                .findIncidentsByUploadId(id, PageRequest.of(0, MAX_UPLOAD_INCIDENTS))
                .stream()
                .map(IncidentSummaryResponse::from)
                .toList();
        return UploadDetailResponse.from(upload, incidents);
    }

    private void persist(Long uploadId, ParseResult parseResult, Instant uploadedAt) {
        Set<Severity> incidentSeverities = incidentSeverities();
        GroupingResult grouping = incidentGrouper.group(parseResult.lines(), incidentSeverities, uploadedAt);

        try {
            transactionTemplate.executeWithoutResult(status -> doPersist(uploadId, parseResult, grouping));
        } catch (DataIntegrityViolationException ex) {
            // A concurrent upload inserted the same fingerprint first. The whole
            // transaction rolled back, so retry once; this time the row exists.
            log.warn("Fingerprint race detected for upload {}. Retrying persistence once.", uploadId);
            transactionTemplate.executeWithoutResult(status -> doPersist(uploadId, parseResult, grouping));
        }
    }

    private void doPersist(Long uploadId, ParseResult parseResult, GroupingResult grouping) {
        LogUpload upload = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload " + uploadId + " not found"));

        Map<String, Long> incidentIds = upsertIncidents(grouping);

        entityManager.flush();
        entityManager.clear();

        int entriesInChunk = 0;
        List<LogEntry> chunk = new ArrayList<>(CHUNK_SIZE);
        LogUpload uploadReference = null;

        for (EntryDraft draft : grouping.entries()) {
            if (chunk.isEmpty()) {
                uploadReference = entityManager.getReference(LogUpload.class, uploadId);
            }

            ParsedLogLine line = draft.line();
            LogEntry entry = new LogEntry();
            entry.setUpload(uploadReference);
            if (draft.fingerprint() != null) {
                entry.setIncident(entityManager.getReference(
                        Incident.class, incidentIds.get(draft.fingerprint())));
            }
            entry.setLineNumber(line.lineNumber());
            entry.setLoggedAt(line.loggedAt());
            entry.setSeverity(line.severity());
            entry.setSeverityInferred(line.severityInferred());
            entry.setServiceName(line.serviceName());
            entry.setLoggerName(line.loggerName());
            entry.setMessage(line.message());
            entry.setStackTrace(line.stackTrace());
            entry.setRawLine(line.rawLine());

            chunk.add(entry);
            entriesInChunk++;
            if (entriesInChunk >= CHUNK_SIZE) {
                logEntryRepository.saveAll(chunk);
                logEntryRepository.flush();
                entityManager.clear();
                chunk.clear();
                entriesInChunk = 0;
            }
        }

        if (!chunk.isEmpty()) {
            logEntryRepository.saveAll(chunk);
            logEntryRepository.flush();
            entityManager.clear();
        }

        LogUpload managed = uploadRepository.findById(uploadId)
                .orElseThrow(() -> new ResourceNotFoundException("Upload " + uploadId + " not found"));
        managed.setTotalLines(parseResult.totalLines());
        managed.setParsedEntries(parseResult.lines().size());
        managed.setMalformedEntries(parseResult.malformedLines());
        managed.setIncidentCount(grouping.groups().size());
        managed.setStatus(UploadStatus.COMPLETED);
        managed.setCompletedAt(Instant.now());
        managed.setErrorMessage(null);
    }

    private Map<String, Long> upsertIncidents(GroupingResult grouping) {
        Map<String, Long> incidentIds = new HashMap<>();
        for (IncidentGroup group : grouping.groups().values()) {
            Incident incident = incidentRepository.findByFingerprint(group.getFingerprint()).orElse(null);
            if (incident == null) {
                incident = new Incident(
                        group.getFingerprint(),
                        group.getTitle(),
                        group.getSampleMessage(),
                        group.getServiceName(),
                        group.getSeverity(),
                        IncidentStatus.OPEN,
                        group.getCount(),
                        group.getFirstSeen(),
                        group.getLastSeen()
                );
                incident = incidentRepository.saveAndFlush(incident);
            } else {
                incident.setOccurrenceCount(incident.getOccurrenceCount() + group.getCount());
                if (group.getFirstSeen().isBefore(incident.getFirstSeen())) {
                    incident.setFirstSeen(group.getFirstSeen());
                }
                if (group.getLastSeen().isAfter(incident.getLastSeen())) {
                    incident.setLastSeen(group.getLastSeen());
                }
                if (incident.getServiceName() == null) {
                    incident.setServiceName(group.getServiceName());
                }
            }
            incidentIds.put(group.getFingerprint(), incident.getId());
        }
        return incidentIds;
    }

    private void markFailed(Long uploadId, RuntimeException cause) {
        try {
            uploadRepository.findById(uploadId).ifPresent(upload -> {
                upload.setStatus(UploadStatus.FAILED);
                upload.setErrorMessage(truncate(rootMessage(cause), MAX_ERROR_MESSAGE_LENGTH));
                upload.setCompletedAt(Instant.now());
                uploadRepository.save(upload);
            });
        } catch (RuntimeException ex) {
            log.error("Could not mark upload {} as FAILED", uploadId, ex);
        }
    }

    private String validateAndSanitize(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidUploadException("The uploaded file is empty.");
        }

        long maxBytes = appProperties.getUpload().getMaxFileSizeBytes();
        if (file.getSize() > maxBytes) {
            throw new FileTooLargeException("File is too large. The maximum allowed size is "
                    + (maxBytes / (1024 * 1024)) + " MB.");
        }

        String filename = sanitizeFilename(file.getOriginalFilename());
        String lowerName = filename.toLowerCase(Locale.ROOT);
        boolean allowedExtension = appProperties.getUpload().getAllowedExtensions().stream()
                .anyMatch(extension -> lowerName.endsWith(extension.toLowerCase(Locale.ROOT)));
        if (!allowedExtension) {
            throw new UnsupportedFileTypeException(
                    "Unsupported file extension. Upload a .log or .txt file.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank()
                && !contentType.toLowerCase(Locale.ROOT).startsWith("text/")
                && !contentType.equalsIgnoreCase("application/octet-stream")
                && !contentType.equalsIgnoreCase("application/x-log")) {
            throw new UnsupportedFileTypeException(
                    "Unsupported content type '" + contentType + "'. Upload a .log or .txt file.");
        }

        return filename;
    }

    private String sanitizeFilename(String originalFilename) {
        String name = originalFilename == null ? "" : originalFilename;
        // Strip any client-side path so a crafted name cannot influence storage.
        name = name.replace('\\', '/');
        int lastSlash = name.lastIndexOf('/');
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "_").strip();
        if (name.isBlank()) {
            name = "upload.log";
        }
        if (name.length() > MAX_FILENAME_LENGTH) {
            String extension = "";
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                extension = name.substring(dot);
            }
            int keep = Math.max(1, MAX_FILENAME_LENGTH - extension.length());
            name = name.substring(0, keep) + extension;
        }
        return name;
    }

    private Set<Severity> incidentSeverities() {
        List<Severity> configured = appProperties.getLog().getIncidentSeverities();
        if (configured == null || configured.isEmpty()) {
            return EnumSet.noneOf(Severity.class);
        }
        return EnumSet.copyOf(configured);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        String message = null;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                message = current.getMessage();
            }
            current = current.getCause();
        }
        if (message == null) {
            message = throwable.getClass().getSimpleName();
        }
        return message;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private void validatePaging(int page, int size) {
        if (page < 0) {
            throw new com.bugtrace.exception.InvalidRequestException("'page' must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new com.bugtrace.exception.InvalidRequestException(
                    "'size' must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
    }
}
