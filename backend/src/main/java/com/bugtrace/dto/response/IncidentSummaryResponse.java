package com.bugtrace.dto.response;

import com.bugtrace.entity.Incident;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;

import java.time.Instant;

public record IncidentSummaryResponse(
        Long id,
        String title,
        String serviceName,
        Severity severity,
        IncidentStatus status,
        long occurrenceCount,
        Instant firstSeen,
        Instant lastSeen
) {

    public static IncidentSummaryResponse from(Incident incident) {
        return new IncidentSummaryResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getServiceName(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getOccurrenceCount(),
                incident.getFirstSeen(),
                incident.getLastSeen()
        );
    }
}
