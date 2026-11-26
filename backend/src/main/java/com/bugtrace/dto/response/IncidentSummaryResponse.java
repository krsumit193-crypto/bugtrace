package com.bugtrace.dto.response;

import com.bugtrace.entity.Incident;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;

import java.time.Instant;
import java.util.List;

public record IncidentSummaryResponse(
        Long id,
        String title,
        String serviceName,
        Severity severity,
        IncidentStatus status,
        long occurrenceCount,
        Instant firstSeen,
        Instant lastSeen,
        Instant resolvedAt,
        Instant snoozedUntil,
        boolean snoozed,
        List<String> tags
) {

    public static IncidentSummaryResponse from(Incident incident) {
        return from(incident, List.of());
    }

    public static IncidentSummaryResponse from(Incident incident, List<String> tags) {
        Instant snoozedUntil = incident.getSnoozedUntil();
        return new IncidentSummaryResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getServiceName(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getOccurrenceCount(),
                incident.getFirstSeen(),
                incident.getLastSeen(),
                incident.getResolvedAt(),
                snoozedUntil,
                snoozedUntil != null && snoozedUntil.isAfter(Instant.now()),
                tags == null ? List.of() : List.copyOf(tags)
        );
    }
}
