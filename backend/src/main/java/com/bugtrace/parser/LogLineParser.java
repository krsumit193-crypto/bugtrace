package com.bugtrace.parser;

import com.bugtrace.enums.Severity;
import com.bugtrace.exception.InvalidUploadException;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses common application log formats line by line.
 *
 * <p>Primary format:</p>
 * <pre>
 * 2026-11-23 10:15:20 ERROR [payment-service] PaymentService - Database connection failed
 * </pre>
 *
 * <p>Also supported:</p>
 * <ul>
 *   <li>an ISO-style {@code T} between date and time;</li>
 *   <li>fractional seconds (up to nanoseconds);</li>
 *   <li>a line without a timestamp, starting with a level token;</li>
 *   <li>a line without a level, where the level is inferred from keywords;</li>
 *   <li>stack-trace continuation lines beginning with whitespace + "at ", "Caused by:",
 *       "Suppressed:" or "... ".</li>
 * </ul>
 *
 * <p>Every other non-blank line is counted as malformed. A malformed line never
 * aborts the upload.</p>
 */
@Component
public class LogLineParser {

    private static final String TIMESTAMP_EXPR =
            "\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d{1,9})?";

    private static final String LEVEL_EXPR =
            "TRACE|VERBOSE|DEBUG|INFO|NOTICE|WARN|WARNING|ERROR|ERR|SEVERE|ALERT|FATAL|CRITICAL|PANIC";

    private static final Pattern TIMESTAMP_LEVEL = Pattern.compile(
            "^(" + TIMESTAMP_EXPR + ")\\s+(" + LEVEL_EXPR + ")\\b\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TIMESTAMP_ONLY = Pattern.compile(
            "^(" + TIMESTAMP_EXPR + ")\\s+(.*)$");

    private static final Pattern LEVEL_ONLY = Pattern.compile(
            "^(" + LEVEL_EXPR + ")\\b\\s*(.*)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern SERVICE = Pattern.compile("^\\[([^\\]]{1,150})]\\s*(.*)$");

    /**
     * A logger token has no whitespace and is followed by " - ". This matches
     * both simple names ("PaymentService") and package names
     * ("com.acme.payment.PaymentService").
     */
    private static final Pattern LOGGER_AND_MESSAGE = Pattern.compile("^([^\\s]{1,255})\\s+-\\s+(.+)$");

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .optionalEnd()
            .toFormatter();

    /** MySQL TEXT columns hold 65,535 bytes; keep a safe character limit. */
    private static final int MAX_TEXT_CHARS = 16_000;
    /** MySQL MEDIUMTEXT holds 16 MB; cap pathological stack traces well below it. */
    private static final int MAX_STACK_TRACE_CHARS = 1_000_000;

    private final SeverityDetector severityDetector;

    public LogLineParser(SeverityDetector severityDetector) {
        this.severityDetector = severityDetector;
    }

    /**
     * Parses every line from the reader. The reader is not closed by this method.
     *
     * @param reader source of the log file
     * @param zone   zone used to convert timestamps (log lines carry no zone)
     */
    public ParseResult parse(BufferedReader reader, ZoneId zone) throws IOException {
        List<ParsedLogLine> parsedLines = new ArrayList<>();
        StringBuilder stackTrace = new StringBuilder();
        ParsedLogLine current = null;
        int totalLines = 0;
        int malformedLines = 0;

        String rawLine;
        while ((rawLine = reader.readLine()) != null) {
            totalLines++;

            if (rawLine.indexOf('\0') >= 0) {
                throw new InvalidUploadException(
                        "The file appears to be binary (NUL byte found). "
                                + "Upload a plain-text .log or .txt file.");
            }

            if (rawLine.isBlank()) {
                // Blank lines carry no information and are not malformed.
                continue;
            }

            if (isContinuation(rawLine)) {
                if (current == null) {
                    malformedLines++;
                } else {
                    if (stackTrace.length() > 0) {
                        stackTrace.append('\n');
                    }
                    int remaining = MAX_STACK_TRACE_CHARS - stackTrace.length();
                    if (remaining > 0) {
                        stackTrace.append(rawLine, 0, Math.min(rawLine.length(), remaining));
                    }
                }
                continue;
            }

            if (current != null) {
                parsedLines.add(withStackTrace(current, stackTrace));
                current = null;
                stackTrace.setLength(0);
            }

            ParsedLogLine parsed = parseSingleLine(totalLines, rawLine, zone);
            if (parsed == null) {
                malformedLines++;
            } else {
                current = parsed;
            }
        }

        if (current != null) {
            parsedLines.add(withStackTrace(current, stackTrace));
        }

        return new ParseResult(List.copyOf(parsedLines), totalLines, malformedLines);
    }

    private ParsedLogLine parseSingleLine(int lineNumber, String rawLine, ZoneId zone) {
        Matcher matcher = TIMESTAMP_LEVEL.matcher(rawLine);
        if (matcher.matches()) {
            Instant loggedAt = parseTimestamp(matcher.group(1), zone);
            if (loggedAt == null) {
                return null;
            }
            Severity severity = severityDetector.fromToken(matcher.group(2)).orElse(null);
            return build(lineNumber, loggedAt, severity, false, matcher.group(3), rawLine);
        }

        matcher = TIMESTAMP_ONLY.matcher(rawLine);
        if (matcher.matches()) {
            Instant loggedAt = parseTimestamp(matcher.group(1), zone);
            if (loggedAt == null) {
                return null;
            }
            String rest = matcher.group(2);
            Optional<Severity> inferred = severityDetector.infer(rest);
            return build(lineNumber, loggedAt, inferred.orElse(null), true, rest, rawLine);
        }

        matcher = LEVEL_ONLY.matcher(rawLine);
        if (matcher.matches()) {
            Severity severity = severityDetector.fromToken(matcher.group(1)).orElse(null);
            if (severity != null) {
                return build(lineNumber, null, severity, false, matcher.group(2), rawLine);
            }
        }

        return null;
    }

    private ParsedLogLine build(int lineNumber, Instant loggedAt, Severity severity,
                                boolean severityInferred, String restRaw, String rawLine) {
        String rest = restRaw == null ? "" : restRaw.strip();

        String serviceName = null;
        Matcher serviceMatcher = SERVICE.matcher(rest);
        if (serviceMatcher.matches()) {
            serviceName = serviceMatcher.group(1).strip();
            if (serviceName.isEmpty()) {
                serviceName = null;
            }
            rest = serviceMatcher.group(2).strip();
        }

        String loggerName = null;
        String message = rest;
        Matcher loggerMatcher = LOGGER_AND_MESSAGE.matcher(rest);
        if (loggerMatcher.matches()) {
            loggerName = loggerMatcher.group(1);
            message = loggerMatcher.group(2).strip();
        }

        if (message.isBlank()) {
            return null;
        }

        return new ParsedLogLine(
                lineNumber,
                loggedAt,
                severity,
                severityInferred,
                serviceName,
                loggerName,
                truncate(message, MAX_TEXT_CHARS),
                null,
                truncate(rawLine, MAX_TEXT_CHARS)
        );
    }

    private ParsedLogLine withStackTrace(ParsedLogLine line, StringBuilder stackTrace) {
        if (stackTrace.length() == 0) {
            return line;
        }
        return new ParsedLogLine(
                line.lineNumber(),
                line.loggedAt(),
                line.severity(),
                line.severityInferred(),
                line.serviceName(),
                line.loggerName(),
                line.message(),
                truncate(stackTrace.toString(), MAX_STACK_TRACE_CHARS),
                line.rawLine()
        );
    }

    private boolean isContinuation(String rawLine) {
        if (rawLine.isEmpty() || !Character.isWhitespace(rawLine.charAt(0))) {
            return false;
        }
        String trimmed = rawLine.stripLeading();
        return trimmed.startsWith("at ")
                || trimmed.startsWith("Caused by:")
                || trimmed.startsWith("Suppressed:")
                || trimmed.startsWith("... ");
    }

    private Instant parseTimestamp(String token, ZoneId zone) {
        String normalized = token.replace(',', '.').replace('T', ' ');
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(normalized, TIMESTAMP_FORMATTER);
            return localDateTime.atZone(zone).toInstant();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
