package com.bugtrace.parser;

import com.bugtrace.enums.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeverityDetectorTest {

    private final SeverityDetector detector = new SeverityDetector();

    @Test
    void mapsKnownLevelTokens() {
        assertThat(detector.fromToken("ERROR")).contains(Severity.ERROR);
        assertThat(detector.fromToken("warning")).contains(Severity.WARN);
        assertThat(detector.fromToken("SEVERE")).contains(Severity.ERROR);
        assertThat(detector.fromToken("CRITICAL")).contains(Severity.FATAL);
        assertThat(detector.fromToken("TRACE")).contains(Severity.DEBUG);
        assertThat(detector.fromToken("nonsense")).isEmpty();
    }

    @Test
    void infersSeverityFromKeywords() {
        assertThat(detector.infer("Database connection failed")).contains(Severity.ERROR);
        assertThat(detector.infer("Out of memory while validating token")).contains(Severity.FATAL);
        assertThat(detector.infer("Retry attempt scheduled")).contains(Severity.WARN);
        assertThat(detector.infer("Application started successfully")).contains(Severity.INFO);
        assertThat(detector.infer("Debug payload received")).contains(Severity.DEBUG);
    }

    @Test
    void returnsEmptyWhenNothingMatches() {
        assertThat(detector.infer("hello world")).isEmpty();
        assertThat(detector.infer("")).isEmpty();
    }
}
