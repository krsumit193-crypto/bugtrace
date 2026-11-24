package com.bugtrace.dto.response;

import com.bugtrace.entity.Incident;
import com.bugtrace.enums.Severity;

/**
 * A heuristic suggestion only. The frontend labels these as "possibly related";
 * they are never merged automatically.
 */
public record RelatedIncidentResponse(
        Long id,
        String title,
        String serviceName,
        Severity severity,
        long occurrenceCount,
        double similarity
) {

    public static RelatedIncidentResponse from(Incident incident, double similarity) {
        return new RelatedIncidentResponse(
                incident.getId(),
                incident.getTitle(),
                incident.getServiceName(),
                incident.getSeverity(),
                incident.getOccurrenceCount(),
                Math.round(similarity * 100.0) / 100.0
        );
    }
}
