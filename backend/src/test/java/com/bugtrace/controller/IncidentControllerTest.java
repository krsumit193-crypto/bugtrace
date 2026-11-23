package com.bugtrace.controller;

import com.bugtrace.exception.ResourceNotFoundException;
import com.bugtrace.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
