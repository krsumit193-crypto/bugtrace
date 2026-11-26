package com.bugtrace.controller;

import com.bugtrace.dto.response.LogEntryResponse;
import com.bugtrace.dto.response.PageResponse;
import com.bugtrace.service.LogEntrySearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LogEntryController.class)
class LogEntryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LogEntrySearchService searchService;

    @Test
    void searchDelegatesFiltersToService() throws Exception {
        when(searchService.search(anyString(), any(), anyString(), any(), any(),
                any(), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true, true));

        mockMvc.perform(get("/api/entries")
                        .param("q", "timeout")
                        .param("severity", "ERROR")
                        .param("serviceName", "payment")
                        .param("sort", "oldest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        verify(searchService).search(eq("timeout"), eq(com.bugtrace.enums.Severity.ERROR),
                eq("payment"), any(), any(), any(), any(),
                eq("oldest"), eq(0), eq(20));
    }

    @Test
    void invalidSeverityReturnsUniform400Error() throws Exception {
        mockMvc.perform(get("/api/entries").param("severity", "BAD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/entries"));
    }

    @Test
    void oversizedPageReturns400() throws Exception {
        mockMvc.perform(get("/api/entries").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidPageNumberReturns400() throws Exception {
        mockMvc.perform(get("/api/entries").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
