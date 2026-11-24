package com.bugtrace.service;

import com.bugtrace.entity.Incident;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentCsvExporterTest {

    @Test
    void writesBomHeaderAndCrlfLineEndings() {
        String csv = IncidentCsvExporter.toCsv(List.of(incident(
                1L, "fp-1", "Connection refused", "payments", Severity.ERROR, 42)));

        assertThat(csv).startsWith(IncidentCsvExporter.BOM + IncidentCsvExporter.HEADER + "\r\n");
        assertThat(csv).contains("1,fp-1,Connection refused,payments,ERROR,OPEN,42,");
        assertThat(csv).endsWith("\r\n");
    }

    @Test
    void emptyListStillProducesHeader() {
        String csv = IncidentCsvExporter.toCsv(List.of());

        assertThat(csv).isEqualTo(IncidentCsvExporter.BOM + IncidentCsvExporter.HEADER + "\r\n");
    }

    @Test
    void quotesFieldsContainingCommasQuotesAndNewlines() {
        Incident incident = incident(
                7L, "fp-7",
                "Timeout after 30s, retrying \"payment\"\nsecond line",
                "billing",
                Severity.FATAL, 3);

        String csv = IncidentCsvExporter.toCsv(List.of(incident));

        assertThat(csv).contains(
                "7,fp-7,\"Timeout after 30s, retrying \"\"payment\"\"\nsecond line\",billing,FATAL,OPEN,3,");
    }

    @Test
    void escapesFormulaInjectionFromUntrustedLogText() {
        Incident incident = incident(
                9L, "fp-9", "=HYPERLINK(\"http://evil\")", "@service", Severity.WARN, 1);

        String csv = IncidentCsvExporter.toCsv(List.of(incident));

        // A leading apostrophe keeps spreadsheets from evaluating the cell.
        assertThat(csv).contains("\"'=HYPERLINK(\"\"http://evil\"\")\"");
        assertThat(csv).contains(",'@service,");
    }

    @Test
    void nullServiceBecomesEmptyField() {
        Incident incident = incident(
                11L, "fp-11", "Disk full", null, Severity.ERROR, 5);

        String csv = IncidentCsvExporter.toCsv(List.of(incident));

        assertThat(csv).contains("11,fp-11,Disk full,,ERROR,OPEN,5,");
    }

    @Test
    void escapeLeavesPlainValuesUntouched() {
        assertThat(IncidentCsvExporter.escape("payments-api")).isEqualTo("payments-api");
        assertThat(IncidentCsvExporter.escape(null)).isEmpty();
        assertThat(IncidentCsvExporter.escape("")).isEmpty();
    }

    @Test
    void escapeHandlesCarriageReturn() {
        assertThat(IncidentCsvExporter.escape("line1\rline2")).isEqualTo("\"line1\rline2\"");
    }

    private static Incident incident(Long id, String fingerprint, String title,
                                    String service, Severity severity, long count) {
        Instant firstSeen = Instant.parse("2026-11-24T10:00:00Z");
        Incident incident = new Incident(
                fingerprint,
                title,
                title,
                service,
                severity,
                IncidentStatus.OPEN,
                count,
                firstSeen,
                firstSeen.plusSeconds(600)
        );
        incident.setId(id);
        return incident;
    }
}
