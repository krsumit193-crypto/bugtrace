package com.bugtrace.dto.request;

import com.bugtrace.enums.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateIncidentStatusRequest(
        @NotNull(message = "status is required")
        IncidentStatus status
) {
}
