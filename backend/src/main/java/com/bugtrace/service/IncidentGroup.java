package com.bugtrace.service;

import com.bugtrace.enums.Severity;
import com.bugtrace.parser.ParsedLogLine;

import java.time.Instant;

/**
 * Aggregate of all occurrences of one fingerprint inside a single upload.
 */
public class IncidentGroup {

    private static final int TITLE_MAX_LENGTH = 500;

    private final String fingerprint;
    private final Severity severity;
    private final String serviceName;
    private final String sampleMessage;
    private final String title;

    private long count;
    private Instant firstSeen;
    private Instant lastSeen;

    public IncidentGroup(String fingerprint, ParsedLogLine firstLine, Instant fallbackTime) {
        this.fingerprint = fingerprint;
        this.severity = firstLine.severity();
        this.serviceName = firstLine.serviceName();
        this.sampleMessage = firstLine.message();
        this.title = toTitle(firstLine.message());
        add(firstLine, fallbackTime);
    }

    public void add(ParsedLogLine line, Instant fallbackTime) {
        count++;
        Instant seen = line.loggedAt() == null ? fallbackTime : line.loggedAt();
        if (firstSeen == null || seen.isBefore(firstSeen)) {
            firstSeen = seen;
        }
        if (lastSeen == null || seen.isAfter(lastSeen)) {
            lastSeen = seen;
        }
    }

    private static String toTitle(String message) {
        String singleLine = message.replaceAll("\\s+", " ").strip();
        if (singleLine.length() <= TITLE_MAX_LENGTH) {
            return singleLine;
        }
        return singleLine.substring(0, TITLE_MAX_LENGTH - 3) + "...";
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getSampleMessage() {
        return sampleMessage;
    }

    public String getTitle() {
        return title;
    }

    public long getCount() {
        return count;
    }

    public Instant getFirstSeen() {
        return firstSeen;
    }

    public Instant getLastSeen() {
        return lastSeen;
    }
}
