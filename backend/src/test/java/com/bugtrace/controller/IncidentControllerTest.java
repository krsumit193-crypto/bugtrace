package com.bugtrace.controller;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        when(incidentService.exportCsv(any(), any(), any(), any(), any(), any()))
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
        when(incidentService.exportCsv(any(), any(), any(), any(), any(), any()))
                .thenThrow(new com.bugtrace.exception.InvalidRequestException(
                        "'from' must be on or before 'to'."));

        mockMvc.perform(get("/api/incidents/export")
                        .param("from", "2026-11-25")
                        .param("to", "2026-11-24"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("'from' must be on or before 'to'."));
    }
}
