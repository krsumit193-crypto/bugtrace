package com.bugtrace.service;

import com.bugtrace.enums.Severity;
import com.bugtrace.parser.FingerprintGenerator;
import com.bugtrace.parser.MessageNormalizer;
import com.bugtrace.parser.ParsedLogLine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentGrouperTest {

    private final IncidentGrouper grouper =
            new IncidentGrouper(new MessageNormalizer(), new FingerprintGenerator());

    @Test
    void groupsNormalizedMessagesAndKeepsTheFirstSample() {
        ParsedLogLine first = line(1, "2026-11-23T10:00:00Z", Severity.ERROR,
                "Database connection failed for order 5512");
        ParsedLogLine second = line(2, "2026-11-23T10:05:00Z", Severity.ERROR,
                "Database connection failed for order 9999");
        ParsedLogLine info = line(3, "2026-11-23T10:06:00Z", Severity.INFO,
                "Database connection failed for order 1000");

        GroupingResult result = grouper.group(List.of(first, second, info),
                Set.of(Severity.WARN, Severity.ERROR, Severity.FATAL),
                Instant.parse("2026-11-23T10:10:00Z"));

        assertThat(result.groups()).hasSize(1);
        IncidentGroup group = result.groups().values().iterator().next();
        assertThat(group.getCount()).isEqualTo(2);
        assertThat(group.getSampleMessage()).isEqualTo("Database connection failed for order 5512");
        assertThat(group.getFirstSeen()).isEqualTo(Instant.parse("2026-11-23T10:00:00Z"));
        assertThat(group.getLastSeen()).isEqualTo(Instant.parse("2026-11-23T10:05:00Z"));
        assertThat(result.entries()).hasSize(3);
        assertThat(result.entries().get(2).fingerprint()).isNull();
    }

    @Test
    void differentLevelsNeverMerge() {
        ParsedLogLine warn = line(1, "2026-11-23T10:00:00Z", Severity.WARN, "Payment retry failed");
        ParsedLogLine error = line(2, "2026-11-23T10:00:01Z", Severity.ERROR, "Payment retry failed");

        GroupingResult result = grouper.group(List.of(warn, error),
                Set.of(Severity.WARN, Severity.ERROR, Severity.FATAL),
                Instant.parse("2026-11-23T10:10:00Z"));

        assertThat(result.groups()).hasSize(2);
    }

    @Test
    void usesFallbackTimeWhenLineHasNoTimestamp() {
        ParsedLogLine line = line(1, null, Severity.ERROR, "Connection refused");

        GroupingResult result = grouper.group(List.of(line),
                Set.of(Severity.ERROR), Instant.parse("2026-11-23T12:00:00Z"));

        IncidentGroup group = result.groups().values().iterator().next();
        assertThat(group.getFirstSeen()).isEqualTo(Instant.parse("2026-11-23T12:00:00Z"));
        assertThat(group.getLastSeen()).isEqualTo(Instant.parse("2026-11-23T12:00:00Z"));
    }

    private ParsedLogLine line(int number, String timestamp, Severity severity, String message) {
        return new ParsedLogLine(
                number,
                timestamp == null ? null : Instant.parse(timestamp),
                severity,
                false,
                "payment-service",
                "PaymentService",
                message,
                null,
                message
        );
    }
}
