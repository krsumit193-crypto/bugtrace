package com.bugtrace.service;

import com.bugtrace.config.AppProperties;
import com.bugtrace.dto.response.BulkStatusResponse;
import com.bugtrace.dto.response.IncidentNoteResponse;
import com.bugtrace.dto.response.IncidentSummaryResponse;
import com.bugtrace.entity.Incident;
import com.bugtrace.entity.IncidentActivity;
import com.bugtrace.entity.IncidentNote;
import com.bugtrace.entity.IncidentTag;
import com.bugtrace.enums.IncidentActivityType;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import com.bugtrace.exception.InvalidRequestException;
import com.bugtrace.exception.ResourceNotFoundException;
import com.bugtrace.parser.SimilarityScorer;
import com.bugtrace.repository.IncidentActivityRepository;
import com.bugtrace.repository.IncidentNoteRepository;
import com.bugtrace.repository.IncidentRepository;
import com.bugtrace.repository.IncidentTagRepository;
import com.bugtrace.repository.LogEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
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
    private IncidentTagRepository tagRepository;

    @Mock
    private IncidentActivityRepository activityRepository;

    @Mock
    private SimilarityScorer similarityScorer;

    private IncidentService service;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getLog().setTimezone("UTC");
        service = new IncidentService(
                incidentRepository, noteRepository, logEntryRepository,
                tagRepository, activityRepository, similarityScorer, properties);
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

    @Test
    void resolvingAnIncidentSetsResolvedAtAndRecordsActivity() {
        Incident incident = incident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        service.updateStatus(1L, IncidentStatus.RESOLVED);

        assertThat(incident.getResolvedAt()).isNotNull();
        ArgumentCaptor<IncidentActivity> activity = ArgumentCaptor.forClass(IncidentActivity.class);
        verify(activityRepository).save(activity.capture());
        assertThat(activity.getValue().getType()).isEqualTo(IncidentActivityType.STATUS_CHANGED);
        assertThat(activity.getValue().getDetail()).isEqualTo("OPEN -> RESOLVED");
    }

    @Test
    void reopeningAnIncidentClearsResolvedAt() {
        Incident incident = incident(1L);
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setResolvedAt(Instant.parse("2026-11-23T12:00:00Z"));
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        service.updateStatus(1L, IncidentStatus.INVESTIGATING);

        assertThat(incident.getResolvedAt()).isNull();
    }

    @Test
    void sameStatusDoesNotCreateAnActivity() {
        Incident incident = incident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        service.updateStatus(1L, IncidentStatus.OPEN);

        verify(activityRepository, never()).save(any(IncidentActivity.class));
    }

    @Test
    void bulkUpdateChangesEveryIncidentAndCountsOnlyRealChanges() {
        Incident first = incident(1L);
        Incident second = incident(2L);
        second.setStatus(IncidentStatus.RESOLVED);
        when(incidentRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(first, second));

        BulkStatusResponse response = service.bulkUpdateStatus(
                List.of(1L, 2L), IncidentStatus.RESOLVED);

        assertThat(response.updated()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(second.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(first.getResolvedAt()).isNotNull();
    }

    @Test
    void bulkUpdateRejectsAMissingIncident() {
        when(incidentRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(incident(1L)));

        assertThatThrownBy(() -> service.bulkUpdateStatus(List.of(1L, 2L), IncidentStatus.RESOLVED))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void replaceTagsNormalisesDeduplicatesAndAddsOnlyTheDifference() {
        Incident incident = incident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(tagRepository.findByIncidentIdOrderByTagAsc(1L))
                .thenReturn(List.of(new IncidentTag(1L, "database", Instant.now())));

        List<String> result = service.replaceTags(
                1L, List.of(" Database ", "payment", "database"));

        assertThat(result).containsExactly("database", "payment");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<IncidentTag>> saved = ArgumentCaptor.forClass(Iterable.class);
        verify(tagRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(IncidentTag::getTag).containsExactly("payment");
        verify(activityRepository).save(any(IncidentActivity.class));
    }

    @Test
    void replaceTagsRemovesTagsThatAreNoLongerPresent() {
        Incident incident = incident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));
        when(tagRepository.findByIncidentIdOrderByTagAsc(1L))
                .thenReturn(List.of(
                        new IncidentTag(1L, "database", Instant.now()),
                        new IncidentTag(1L, "legacy", Instant.now())));

        List<String> result = service.replaceTags(1L, List.of("database"));

        assertThat(result).containsExactly("database");
        verify(tagRepository).deleteAll(anyList());
    }

    @Test
    void replaceTagsRejectsInvalidAndTooManyTags() {
        Incident incident = incident(1L);
        when(incidentRepository.findById(1L)).thenReturn(Optional.of(incident));

        assertThatThrownBy(() -> service.replaceTags(1L, List.of("bad tag")))
                .isInstanceOf(InvalidRequestException.class);

        List<String> tooMany = java.util.stream.IntStream.rangeClosed(1, 11)
                .mapToObj(index -> "tag" + index)
                .toList();
        assertThatThrownBy(() -> service.replaceTags(1L, tooMany))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("at most");
    }

    @Test
    void listTagsReturnsTheDistinctRepositoryResult() {
        when(tagRepository.findDistinctTags()).thenReturn(List.of("database", "payment"));

        assertThat(service.listTags()).containsExactly("database", "payment");
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
