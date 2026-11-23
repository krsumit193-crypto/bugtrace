package com.bugtrace.parser;

import com.bugtrace.enums.Severity;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LogLineParserTest {

    private final LogLineParser parser = new LogLineParser(new SeverityDetector());

    @Test
    void parsesTheDocumentedFormat() throws IOException {
        ParseResult result = parse(
                "2026-11-23 10:15:20 ERROR [payment-service] PaymentService - Database connection failed");

        assertThat(result.totalLines()).isEqualTo(1);
        assertThat(result.malformedLines()).isZero();
        assertThat(result.lines()).hasSize(1);

        ParsedLogLine line = result.lines().get(0);
        assertThat(line.lineNumber()).isEqualTo(1);
        assertThat(line.loggedAt()).isEqualTo(Instant.parse("2026-11-23T10:15:20Z"));
        assertThat(line.severity()).isEqualTo(Severity.ERROR);
        assertThat(line.severityInferred()).isFalse();
        assertThat(line.serviceName()).isEqualTo("payment-service");
        assertThat(line.loggerName()).isEqualTo("PaymentService");
        assertThat(line.message()).isEqualTo("Database connection failed");
        assertThat(line.rawLine()).contains("Database connection failed");
    }

    @Test
    void attachesStackTracesToThePreviousEntry() throws IOException {
        ParseResult result = parse(
                "2026-11-23 10:15:20 ERROR [payment-service] PaymentService - Database connection failed",
                "\tat com.acme.PaymentRepository.find(PaymentRepository.java:42)",
                "\tCaused by: java.sql.SQLException: Connection refused");

        assertThat(result.malformedLines()).isZero();
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).stackTrace())
                .contains("PaymentRepository.java:42")
                .contains("Caused by: java.sql.SQLException: Connection refused");
    }

    @Test
    void infersSeverityWhenTheLevelIsMissing() throws IOException {
        ParseResult result = parse(
                "2026-11-23 10:15:20 [payment-service] PaymentService - Database connection failed");

        ParsedLogLine line = result.lines().get(0);
        assertThat(line.severity()).isEqualTo(Severity.ERROR);
        assertThat(line.severityInferred()).isTrue();
        assertThat(line.serviceName()).isEqualTo("payment-service");
    }

    @Test
    void storesUnknownSeverityWhenInferenceFindsNothing() throws IOException {
        ParseResult result = parse(
                "2026-11-23 10:15:20 [payment-service] PaymentService - hello world");

        ParsedLogLine line = result.lines().get(0);
        assertThat(line.severity()).isNull();
        assertThat(line.severityInferred()).isTrue();
    }

    @Test
    void acceptsLevelOnlyLinesWithoutTimestamp() throws IOException {
        ParseResult result = parse("WARN [order-service] OrderService - retry later");

        ParsedLogLine line = result.lines().get(0);
        assertThat(line.loggedAt()).isNull();
        assertThat(line.severity()).isEqualTo(Severity.WARN);
        assertThat(line.severityInferred()).isFalse();
    }

    @Test
    void countsMalformedLinesWithoutAborting() throws IOException {
        ParseResult result = parse(
                "this line is not a log line",
                "2026-11-23 10:15:20 INFO [order-service] OrderService - health check passed",
                "",
                "another bad line");

        assertThat(result.totalLines()).isEqualTo(4);
        assertThat(result.lines()).hasSize(1);
        assertThat(result.malformedLines()).isEqualTo(2);
    }

    @Test
    void parsesFractionalSecondsAndIsoSeparator() throws IOException {
        ParseResult result = parse("2026-11-23T10:15:20.123456 INFO [api] Api - started");

        assertThat(result.lines().get(0).loggedAt())
                .isEqualTo(Instant.parse("2026-11-23T10:15:20.123456Z"));
    }

    private ParseResult parse(String... lines) throws IOException {
        String content = String.join("\n", lines);
        return parser.parse(new BufferedReader(new StringReader(content)), ZoneOffset.UTC);
    }
}
