package com.bugtrace.dto.response;

import com.bugtrace.entity.Incident;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;

import java.time.Instant;
import java.util.List;

public record IncidentDetailResponse(
        Long id,
        String fingerprint,
        String title,
        String sampleMessage,
        String serviceName,
        Severity severity,
        IncidentStatus status,
        long occurrenceCount,
        Instant firstSeen,
        Instant lastSeen,
        List<IncidentNoteResponse> notes,
        List<RelatedIncidentResponse> relatedIncidents
) {

    public static IncidentDetailResponse from(Incident incident,
                                              List<IncidentNoteResponse> notes,
                                              List<RelatedIncidentResponse> relatedIncidents) {
        return new IncidentDetailResponse(
                incident.getId(),
                incident.getFingerprint(),
                incident.getTitle(),
                incident.getSampleMessage(),
                incident.getServiceName(),
                incident.getSeverity(),
                incident.getStatus(),
                incident.getOccurrenceCount(),
                incident.getFirstSeen(),
                incident.getLastSeen(),
                notes,
                relatedIncidents
        );
    }
}
