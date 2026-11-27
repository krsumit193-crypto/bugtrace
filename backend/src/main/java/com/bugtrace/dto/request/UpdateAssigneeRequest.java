package com.bugtrace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateAssigneeRequest(
        @NotBlank(message = "assignee is required")
        @Size(max = 100, message = "assignee must be at most 100 characters")
        String assignee
) {
}
