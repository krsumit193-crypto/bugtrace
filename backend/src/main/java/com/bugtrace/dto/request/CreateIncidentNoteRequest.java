package com.bugtrace.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateIncidentNoteRequest(
        @NotBlank(message = "noteText must not be blank")
        @Size(max = 2000, message = "noteText must be at most 2000 characters")
        String noteText
) {
}
