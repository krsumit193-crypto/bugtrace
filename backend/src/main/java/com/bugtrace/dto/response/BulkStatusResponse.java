package com.bugtrace.dto.response;

import com.bugtrace.enums.IncidentStatus;

/**
 * Result of a bulk status update. {@code updated} counts incidents that were
 * actually changed (an incident already in the target status is not counted).
 */
public record BulkStatusResponse(int updated, IncidentStatus status) {
}
