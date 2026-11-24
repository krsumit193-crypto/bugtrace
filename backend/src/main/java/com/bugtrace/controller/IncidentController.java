package com.bugtrace.controller;

import com.bugtrace.dto.request.CreateIncidentNoteRequest;
import com.bugtrace.dto.request.UpdateIncidentStatusRequest;
import com.bugtrace.dto.response.IncidentDetailResponse;
import com.bugtrace.dto.response.IncidentNoteResponse;
import com.bugtrace.dto.response.IncidentSummaryResponse;
import com.bugtrace.dto.response.LogEntryResponse;
import com.bugtrace.dto.response.PageResponse;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import com.bugtrace.service.IncidentExport;
import com.bugtrace.service.IncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/incidents")
@Validated
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public PageResponse<IncidentSummaryResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return incidentService.list(q, severity, status, from, to, sort, page, size);
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse get(@PathVariable Long id) {
        return incidentService.get(id);
    }

    /**
     * Downloads the incidents matching the same filters as {@link #list}.
     *
     * <p>Returns {@code text/csv} with an attachment filename plus two small
     * diagnostics headers: {@code X-Exported-Rows} and
     * {@code X-Export-Truncated} (true when the 5000 row cap was reached).</p>
     */
    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "latest") String sort) {
        IncidentExport export = incidentService.exportCsv(q, severity, status, from, to, sort);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.filename())
                        .build()
                        .toString())
                .header("X-Exported-Rows", Integer.toString(export.rowCount()))
                .header("X-Export-Truncated", Boolean.toString(export.truncated()))
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(export.content());
    }

    @GetMapping("/{id}/entries")
    public PageResponse<LogEntryResponse> getEntries(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return incidentService.getEntries(id, page, size);
    }

    @PatchMapping("/{id}/status")
    public IncidentSummaryResponse updateStatus(@PathVariable Long id,
                                                @Valid @RequestBody UpdateIncidentStatusRequest request) {
        return incidentService.updateStatus(id, request.status());
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentNoteResponse addNote(@PathVariable Long id,
                                        @Valid @RequestBody CreateIncidentNoteRequest request) {
        return incidentService.addNote(id, request.noteText());
    }
}
