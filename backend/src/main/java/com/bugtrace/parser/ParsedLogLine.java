package com.bugtrace.parser;

import com.bugtrace.enums.Severity;

import java.time.Instant;

/**
 * One parsed log line, before it is persisted.
 *
 * @param lineNumber       1-based line number in the uploaded file
 * @param loggedAt         parsed timestamp, or null when the line has no timestamp
 * @param severity         explicit or inferred severity, or null when unknown
 * @param severityInferred true when the level was not present and was inferred
 * @param serviceName      value from [brackets], or null
 * @param loggerName       logger before " - ", or null
 * @param message          human-readable message
 * @param stackTrace       continuation lines joined with newlines, or null
 * @param rawLine          the original line, preserved for investigation
 */
public record ParsedLogLine(
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
}
