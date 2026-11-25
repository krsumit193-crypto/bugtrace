package com.bugtrace.dto.request;

import com.bugtrace.enums.IncidentStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Update the status of several incidents in one transaction. The cap keeps a
 * single request small enough to stay responsive and easy to audit.
 */
public record BulkUpdateStatusRequest(
        @NotEmpty(message = "ids must contain at least one incident id")
        @Size(max = 100, message = "at most 100 incidents can be updated at once")
        List<@NotNull(message = "incident id must not be null") Long> ids,

        @NotNull(message = "status is required")
        IncidentStatus status
) {
}
