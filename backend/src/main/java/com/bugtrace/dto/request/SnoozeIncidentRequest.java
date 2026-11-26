package com.bugtrace.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Snooze duration for one incident. The upper bound keeps a typo from hiding
 * an incident for years.
 */
public record SnoozeIncidentRequest(
        @NotNull(message = "hours is required")
        @Min(value = 1, message = "hours must be between 1 and 720")
        @Max(value = 720, message = "hours must be between 1 and 720")
        Integer hours
) {
}
