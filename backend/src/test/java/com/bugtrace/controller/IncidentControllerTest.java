package com.bugtrace.controller;

import com.bugtrace.dto.response.BulkStatusResponse;
import com.bugtrace.dto.response.IncidentSummaryResponse;
import com.bugtrace.entity.Incident;
import com.bugtrace.enums.IncidentStatus;
import com.bugtrace.enums.Severity;
import com.bugtrace.exception.ResourceNotFoundException;
import com.bugtrace.service.IncidentExport;
import com.bugtrace.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentService incidentService;

    @Test
    void blankNoteReturnsFieldErrorJson() throws Exception {
        mockMvc.perform(post("/api/incidents/1/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteText\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/incidents/1/notes"))
                .andExpect(jsonPath("$.fieldErrors.noteText").exists());
    }

    @Test
    void unknownIncidentReturns404Json() throws Exception {
        when(incidentService.get(99L))
                .thenThrow(new ResourceNotFoundException("Incident 99 not found"));

        mockMvc.perform(get("/api/incidents/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Incident 99 not found"));
    }

    @Test
    void invalidEnumReturns400() throws Exception {
        mockMvc.perform(get("/api/incidents").param("severity", "BAD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void oversizedPageSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/incidents").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void exportReturnsCsvAttachmentWithRowMetadata() throws Exception {
        IncidentExport export = new IncidentExport(
                "bugtrace-incidents-20261124.csv",
                "\uFEFFid,fingerprint,title\r\n1,fp-1,Connection refused\r\n"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                1,
                false);
        when(incidentService.exportCsv(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(export);

        mockMvc.perform(get("/api/incidents/export").param("severity", "ERROR"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"bugtrace-incidents-20261124.csv\""))
                .andExpect(header().string("X-Exported-Rows", "1"))
                .andExpect(header().string("X-Export-Truncated", "false"))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("\uFEFFid,fingerprint")));
    }

    @Test
    void exportRejectsInvertedDateRange() throws Exception {
        when(incidentService.exportCsv(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new com.bugtrace.exception.InvalidRequestException(
                        "'from' must be on or before 'to'."));

        mockMvc.perform(get("/api/incidents/export")
                        .param("from", "2026-11-25")
                        .param("to", "2026-11-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("'from' must be on or before 'to'."));
    }

    @Test
    void bulkStatusRejectsAnEmptyIdList() throws Exception {
        mockMvc.perform(patch("/api/incidents/bulk-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[],\"status\":\"RESOLVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.ids").exists());
    }

    @Test
    void bulkStatusReturnsTheUpdatedCount() throws Exception {
        when(incidentService.bulkUpdateStatus(any(), any()))
                .thenReturn(new BulkStatusResponse(2, IncidentStatus.RESOLVED));

        mockMvc.perform(patch("/api/incidents/bulk-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[1,2],\"status\":\"RESOLVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void tagsEndpointReturnsSuggestions() throws Exception {
        when(incidentService.listTags()).thenReturn(java.util.List.of("database", "payment"));

        mockMvc.perform(get("/api/incidents/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("database"))
                .andExpect(jsonPath("$[1]").value("payment"));
    }

    @Test
    void assigneesEndpointReturnsSuggestions() throws Exception {
        when(incidentService.listAssignees()).thenReturn(java.util.List.of("alice", "bob"));

        mockMvc.perform(get("/api/incidents/assignees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("alice"))
                .andExpect(jsonPath("$[1]").value("bob"));
    }

    @Test
    void assigningAnOwnerReturnsTheUpdatedIncident() throws Exception {
        when(incidentService.assign(1L, "alice")).thenReturn(summary(false, "alice"));

        mockMvc.perform(patch("/api/incidents/1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"alice\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value("alice"));
    }

    @Test
    void assigningRejectsBlankAndTooLongOwners() throws Exception {
        mockMvc.perform(patch("/api/incidents/1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.assignee").exists());

        mockMvc.perform(patch("/api/incidents/1/assignee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignee\":\"" + "a".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.assignee").exists());
    }

    @Test
    void clearingAnOwnerReturnsAnUnassignedIncident() throws Exception {
        when(incidentService.unassign(1L)).thenReturn(summary(false, null));

        mockMvc.perform(delete("/api/incidents/1/assignee"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignee").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void corsPreflightAllowsPutForTagEdits() throws Exception {
        mockMvc.perform(options("/api/incidents/1/tags")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("PUT")));
    }

    @Test
    void replacingTagsReturnsTheNormalisedSet() throws Exception {
        when(incidentService.replaceTags(any(), any()))
                .thenReturn(java.util.List.of("database"));

        mockMvc.perform(put("/api/incidents/7/tags")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tags\":[\"Database\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("database"));
    }

    @Test
    void snoozeRejectsMissingHours() throws Exception {
        mockMvc.perform(patch("/api/incidents/1/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hours").exists());
    }

    @Test
    void snoozeRejectsHoursAboveTheMaximum() throws Exception {
        mockMvc.perform(patch("/api/incidents/1/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\":999}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.hours").exists());
    }

    @Test
    void snoozeReturnsTheUpdatedIncident() throws Exception {
        when(incidentService.snooze(1L, 24)).thenReturn(summary(true));

        mockMvc.perform(patch("/api/incidents/1/snooze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hours\":24}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snoozed").value(true))
                .andExpect(jsonPath("$.snoozedUntil").exists());
    }

    @Test
    void unsnoozeReturnsTheUpdatedIncident() throws Exception {
        when(incidentService.unsnooze(1L)).thenReturn(summary(false));

        mockMvc.perform(delete("/api/incidents/1/snooze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snoozed").value(false))
                .andExpect(jsonPath("$.snoozedUntil").value(org.hamcrest.Matchers.nullValue()));
    }

    private IncidentSummaryResponse summary(boolean snoozed) {
        return summary(snoozed, null);
    }

    private IncidentSummaryResponse summary(boolean snoozed, String assignee) {
        Incident incident = new Incident(
                "a".repeat(64),
                "Database connection failed",
                "Database connection failed",
                "payment-service",
                Severity.ERROR,
                IncidentStatus.OPEN,
                3,
                Instant.parse("2026-11-23T10:00:00Z"),
                Instant.parse("2026-11-23T10:05:00Z"));
        incident.setId(1L);
        if (assignee != null) {
            incident.setAssignee(assignee);
        }
        if (snoozed) {
            incident.setSnoozedUntil(Instant.now().plusSeconds(3_600));
        }
        return IncidentSummaryResponse.from(incident);
    }
}
