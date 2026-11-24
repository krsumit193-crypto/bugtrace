package com.bugtrace.parser;

import com.bugtrace.enums.Severity;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Turns a level token into a {@link Severity}, and infers a severity from the
 * message text when a log line does not contain an explicit level.
 *
 * <p>The inference is intentionally simple and transparent: it is a keyword
 * classifier, not a language model. The API exposes {@code severityInferred}
 * so the UI can show that a level was guessed.</p>
 */
@Component
public class SeverityDetector {

    private static final Map<String, Severity> EXPLICIT_LEVELS = Map.ofEntries(
            Map.entry("TRACE", Severity.DEBUG),
            Map.entry("VERBOSE", Severity.DEBUG),
            Map.entry("DEBUG", Severity.DEBUG),
            Map.entry("INFO", Severity.INFO),
            Map.entry("NOTICE", Severity.INFO),
            Map.entry("WARN", Severity.WARN),
            Map.entry("WARNING", Severity.WARN),
            Map.entry("ERROR", Severity.ERROR),
            Map.entry("ERR", Severity.ERROR),
            Map.entry("SEVERE", Severity.ERROR),
            Map.entry("FATAL", Severity.FATAL),
            Map.entry("CRITICAL", Severity.FATAL),
            Map.entry("PANIC", Severity.FATAL)
    );

    private static final Pattern FATAL_PATTERN = Pattern.compile(
            "\\b(fatal|panic|critical|out of memory|system (?:shutdown|halted))\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern ERROR_PATTERN = Pattern.compile(
            "\\b(error|exception|failed|failure|fail|cannot|can't|unable|refused|denied|"
                    + "timed? ?out|timeout|aborted|abort|crash(?:ed)?|unreachable|deadlock)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern WARN_PATTERN = Pattern.compile(
            "\\b(warn(?:ing)?|deprecated|retry(?:ing)?|degraded|slow|high latency|"
                    + "disk space|memory usage|fallback|skipped)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern INFO_PATTERN = Pattern.compile(
            "\\b(info|started|starting|stopped|stopping|listening|initialized|initializing|"
                    + "completed|success|succeeded|connected|ready|shutdown|scheduled)\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DEBUG_PATTERN = Pattern.compile(
            "\\b(debug|trace|verbose)\\b",
            Pattern.CASE_INSENSITIVE);

    /**
     * @return the severity for an explicit level token, if the token is known
     */
    public Optional<Severity> fromToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(EXPLICIT_LEVELS.get(token.strip().toUpperCase(Locale.ROOT)));
    }

    /**
     * Infers a severity from free text using ordered keyword rules. The first
     * matching rule wins, so a message containing both "error" and "warning"
     * is classified as ERROR.
     */
    public Optional<Severity> infer(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        if (FATAL_PATTERN.matcher(text).find()) {
            return Optional.of(Severity.FATAL);
        }
        if (ERROR_PATTERN.matcher(text).find()) {
            return Optional.of(Severity.ERROR);
        }
        if (WARN_PATTERN.matcher(text).find()) {
            return Optional.of(Severity.WARN);
        }
        if (INFO_PATTERN.matcher(text).find()) {
            return Optional.of(Severity.INFO);
        }
        if (DEBUG_PATTERN.matcher(text).find()) {
            return Optional.of(Severity.DEBUG);
        }
        return Optional.empty();
    }
}
