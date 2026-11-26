package com.bugtrace.controller;

import com.bugtrace.dto.response.LogEntryResponse;
import com.bugtrace.dto.response.PageResponse;
import com.bugtrace.enums.Severity;
import com.bugtrace.service.LogEntrySearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Global search over stored log lines. All filters are optional and combinable.
 */
@RestController
@RequestMapping("/api/entries")
@Validated
public class LogEntryController {

    private final LogEntrySearchService searchService;

    public LogEntryController(LogEntrySearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public PageResponse<LogEntryResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) String serviceName,
            @RequestParam(required = false) @Positive Long uploadId,
            @RequestParam(required = false) @Positive Long incidentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return searchService.search(q, severity, serviceName, uploadId, incidentId,
                from, to, sort, page, size);
    }
}
