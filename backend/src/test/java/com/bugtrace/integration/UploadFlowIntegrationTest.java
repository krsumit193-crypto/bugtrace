package com.bugtrace.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end HTTP test on H2 (MySQL mode): upload -> parse -> group ->
 * incidents -> notes/status -> dashboard -> second upload updates the same
 * incident instead of creating a duplicate.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UploadFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void uploadParsesGroupsAndServesDashboard() throws Exception {
        byte[] content = new ClassPathResource("sample-app.log").getContentAsByteArray();
        MockMultipartFile file = new MockMultipartFile(
                "file", "sample-app.log", MediaType.TEXT_PLAIN_VALUE, content);

        MvcResult uploadResult = mockMvc.perform(multipart("/api/uploads").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalLines").value(18))
                .andExpect(jsonPath("$.parsedEntries").value(13))
                .andExpect(jsonPath("$.malformedEntries").value(1))
                .andExpect(jsonPath("$.incidentCount").value(5))
                .andReturn();

        JsonNode upload = read(uploadResult);
        long uploadId = upload.path("id").asLong();

        mockMvc.perform(get("/api/uploads/{id}", uploadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidents.length()").value(5));

        MvcResult incidentListResult = mockMvc.perform(get("/api/incidents").param("sort", "count"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode incidentPage = read(incidentListResult);
        assertThat(incidentPage.path("totalElements").asLong()).isEqualTo(5);
        JsonNode topIncident = incidentPage.path("content").get(0);
        assertThat(topIncident.path("occurrenceCount").asLong()).isEqualTo(3);
        long incidentId = topIncident.path("id").asLong();

        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(0))
                .andExpect(jsonPath("$.sampleMessage").value(
                        "Database connection failed for order 5512"));

        MvcResult entriesResult = mockMvc.perform(get("/api/incidents/{id}/entries", incidentId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode entries = read(entriesResult);
        assertThat(entries.path("totalElements").asLong()).isEqualTo(3);
        assertThat(entries.path("content").get(2).path("stackTrace").asText())
                .contains("PaymentRepository.java:42")
                .contains("Connection refused");

        mockMvc.perform(patch("/api/incidents/{id}/status", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVESTIGATING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        mockMvc.perform(post("/api/incidents/{id}/notes", incidentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"noteText\":\"  Checked DB pool size  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.noteText").value("Checked DB pool size"));

        mockMvc.perform(get("/api/incidents/{id}", incidentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notes.length()").value(1))
                .andExpect(jsonPath("$.status").value("INVESTIGATING"));

        MvcResult statsResult = mockMvc.perform(get("/api/dashboard/stats"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode stats = read(statsResult);
        assertThat(stats.path("totalUploads").asLong()).isEqualTo(1);
        assertThat(stats.path("totalLines").asLong()).isEqualTo(18);
        assertThat(stats.path("parsedEntries").asLong()).isEqualTo(13);
        assertThat(stats.path("malformedEntries").asLong()).isEqualTo(1);
        assertThat(stats.path("totalEntries").asLong()).isEqualTo(13);
        assertThat(stats.path("uniqueIncidents").asLong()).isEqualTo(5);
        assertThat(stats.path("openIncidents").asLong()).isEqualTo(4);
        assertThat(stats.path("warnEntries").asLong()).isEqualTo(2);
        assertThat(stats.path("errorEntries").asLong()).isEqualTo(5);
        assertThat(stats.path("fatalEntries").asLong()).isEqualTo(1);
        assertThat(stats.path("topRepeatedErrors").get(0).path("occurrenceCount").asLong()).isEqualTo(3);
        assertThat(stats.path("errorsByService").get(0).path("serviceName").asText())
                .isEqualTo("payment-service");
        assertThat(stats.path("errorsOverTime").size()).isEqualTo(14);
        assertThat(stats.path("recentIncidents").size()).isEqualTo(5);

        String secondLog = "2026-11-24 09:00:00 ERROR [payment-service] PaymentService - "
                + "Database connection failed for order 9999\n";
        MockMultipartFile secondFile = new MockMultipartFile(
                "file", "second.log", MediaType.TEXT_PLAIN_VALUE,
                secondLog.getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/uploads").file(secondFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentCount").value(1));

        mockMvc.perform(get("/api/incidents").param("sort", "count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].occurrenceCount").value(4));
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
