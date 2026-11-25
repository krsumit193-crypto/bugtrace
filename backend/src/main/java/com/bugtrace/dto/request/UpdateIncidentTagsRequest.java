package com.bugtrace.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Replace the complete tag set of one incident. Sending an empty list clears
 * every tag; the service normalises, de-duplicates and validates each value.
 */
public record UpdateIncidentTagsRequest(
        @NotNull(message = "tags is required")
        @Size(max = 10, message = "an incident can have at most 10 tags")
        List<String> tags
) {
}
