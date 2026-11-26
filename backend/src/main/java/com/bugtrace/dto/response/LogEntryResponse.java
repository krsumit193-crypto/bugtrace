package com.bugtrace.dto.response;

import com.bugtrace.entity.LogEntry;
import com.bugtrace.enums.Severity;

import java.time.Instant;

public record LogEntryResponse(
        Long id,
        Long uploadId,
        Long incidentId,
        String uploadFilename,
        int lineNumber,
        Instant loggedAt,
        Severity severity,
        boolean severityInferred,
        String serviceName,
        String loggerName,
        String message,
        String stackTrace,
        String rawLine
) {

    public static LogEntryResponse from(LogEntry entry) {
        return new LogEntryResponse(
                entry.getId(),
                entry.getUpload().getId(),
                entry.getIncident() == null ? null : entry.getIncident().getId(),
                entry.getUpload().getOriginalFilename(),
                entry.getLineNumber(),
                entry.getLoggedAt(),
                entry.getSeverity(),
                entry.isSeverityInferred(),
                entry.getServiceName(),
                entry.getLoggerName(),
                entry.getMessage(),
                entry.getStackTrace(),
                entry.getRawLine()
        );
    }
}
