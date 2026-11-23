package com.bugtrace.dto.response;

import com.bugtrace.entity.IncidentNote;

import java.time.Instant;

public record IncidentNoteResponse(
        Long id,
        String noteText,
        Instant createdAt
) {

    public static IncidentNoteResponse from(IncidentNote note) {
        return new IncidentNoteResponse(note.getId(), note.getNoteText(), note.getCreatedAt());
    }
}
