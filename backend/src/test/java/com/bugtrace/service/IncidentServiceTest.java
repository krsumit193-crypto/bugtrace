package com.bugtrace.service;

import com.bugtrace.config.AppProperties;
import com.bugtrace.dto.response.IncidentNoteResponse;
import com.bugtrace.dto.response.IncidentSummaryResponse;
import com.bugtrace.entity.Incident;
import com.bugtrace.entity.IncidentNote;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import com.bugtrace.exception.InvalidRequestException;
import com.bugtrace.exception.ResourceNotFoundException;
import com.bugtrace.parser.SimilarityScorer;
import com.bugtrace.repository.IncidentNoteRepository;
import com.bugtrace.repository.IncidentRepository;
import com.bugtrace.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

    @Mock
    private IncidentRepository incidentRepository;

    @Mock
    private IncidentNoteRepository noteRepository;

    @Mock
    private LogEntryRepository logEntryRepository;

    @Mock
    private SimilarityScorer similarityScorer;

    private IncidentService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getLog().setTimezone("UTC");
        service = new IncidentService(
                incidentRepository, noteRepository, logEntryRepository, similarityScorer, properties);
    }

    @Test
    void updateStatusChangesTheEntity() {
        Incident incident = incident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        IncidentSummaryResponse response = service.updateStatus(1L, IncidentStatus.RESOLVED);

        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(response.status()).isEqualTo(IncidentStatus.RESOLVED);
    }

    @Test
    void addNoteTrimsTextAndSaves() {
        Incident incident = incident(2L);
        when(incidentRepository.findById(2L)).thenReturn(Optional.of(incident));
        when(noteRepository.save(any(IncidentNote.class))).thenAnswer(invocation -> {
            IncidentNote note = invocation.getArgument(0);
            note.setId(99L);
            return note;
        });

        IncidentNoteResponse response = service.addNote(2L, "  Looked at the connection pool  ");

        assertThat(response.noteText()).isEqualTo("Looked at the connection pool");
        assertThat(response.id()).isEqualTo(99L);
        verify(noteRepository).save(any(IncidentNote.class));
    }

    @Test
    void addNoteRejectsBlankText() {
        assertThatThrownBy(() -> service.addNote(2L, "   "))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void getEntriesRejectsUnknownIncident() {
        when(incidentRepository.existsById(5L)).thenReturn(false);

        assertThatThrownBy(() -> service.getEntries(5L, 0, 20))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listRejectsUnknownSort() {
        assertThatThrownBy(() -> service.list(null, null, null, null, null, "newest", 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("sort");
    }

    @Test
    void listRejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.list(
                null, null, null, LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 23),
                "latest", 0, 20))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from");
    }

    @Test
    void exportCsvWritesMatchingRows() {
        when(incidentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(java.util.List.of(incident(1L))));

        IncidentExport export = service.exportCsv(null, Severity.ERROR, null, null, null, "latest");

        assertThat(export.rowCount()).isEqualTo(1);
        assertThat(export.truncated()).isFalse();
        assertThat(export.filename()).startsWith("bugtrace-incidents-").endsWith(".csv");
        assertThat(new String(export.content(), java.nio.charset.StandardCharsets.UTF_8))
                .contains("Database connection failed")
                .contains("payment-service");
    }

    @Test
    void exportCsvCapsRowsAtLimitAndFlagsTruncation() {
        java.util.List<Incident> rows = java.util.stream.IntStream.rangeClosed(1, 5_001)
                .mapToObj(index -> incident((long) index))
                .toList();
        when(incidentRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class),
                any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new org.springframework.data.domain.PageImpl<>(rows));

        IncidentExport export = service.exportCsv(null, null, null, null, null, "count");

        assertThat(export.rowCount()).isEqualTo(5_000);
        assertThat(export.truncated()).isTrue();
    }

    @Test
    void exportCsvRejectsInvertedDateRange() {
        assertThatThrownBy(() -> service.exportCsv(
                null, null, null, LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 23), "latest"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("from");
    }

    private Incident incident(Long id) {
        Incident incident = new Incident(
                "a".repeat(64),
                "Database connection failed",
                "Database connection failed",
                "payment-service",
                Severity.ERROR,
                IncidentStatus.OPEN,
                3,
                Instant.parse("2026-11-23T10:00:00Z"),
                Instant.parse("2026-11-23T10:05:00Z")
        );
        incident.setId(id);
        return incident;
    }
}
