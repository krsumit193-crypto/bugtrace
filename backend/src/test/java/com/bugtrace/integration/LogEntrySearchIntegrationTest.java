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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uploads the documented sample log and verifies that raw entries can be
 * searched with combined free-text, severity, service, upload and date filters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogEntrySearchIntegrationTest {

    /** Separate H2 database so other integration tests cannot affect these counts. */
    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:bugtrace-search-"
                + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void searchesEntriesAcrossUploadsWithFilters() throws Exception {
        byte[] content = new ClassPathResource("sample-app.log").getContentAsByteArray();
        mockMvc.perform(multipart("/api/uploads").file(new MockMultipartFile(
                        "file", "sample-app.log", MediaType.TEXT_PLAIN_VALUE, content)))
                .andExpect(status().isCreated());

        // Free text is matched case-insensitively against the parsed message.
        JsonNode byText = search(Map.of("q", "connection failed"));
        assertThat(byText.path("totalElements").asLong()).isEqualTo(3);
        assertThat(byText.path("content").get(0).path("message").asText())
                .contains("Database connection failed");

        // Severity and service filters compose with free text.
        JsonNode fatal = search(Map.of("q", "token", "severity", "FATAL"));
        assertThat(fatal.path("totalElements").asLong()).isEqualTo(1);
        assertThat(fatal.path("content").get(0).path("severity").asText()).isEqualTo("FATAL");

        JsonNode service = search(Map.of("q", "timeout", "serviceName", "payment"));
        assertThat(service.path("totalElements").asLong()).isEqualTo(2);

        // INFO/DEBUG lines never become incidents, but they are still searchable.
        JsonNode info = search(Map.of("severity", "INFO"));
        assertThat(info.path("totalElements").asLong()).isEqualTo(4);

        // Sorting by file line number keeps the original order.
        JsonNode lines = search(Map.of("severity", "ERROR", "sort", "line", "size", "100"));
        assertThat(lines.path("content").size()).isEqualTo(5);
        assertThat(lines.path("content").get(0).path("lineNumber").asInt()).isEqualTo(4);

        // Date range and upload id filters.
        JsonNode dated = search(Map.of("from", "2026-11-23", "to", "2026-11-23",
                "severity", "ERROR"));
        assertThat(dated.path("totalElements").asLong()).isEqualTo(5);
        JsonNode byUpload = search(Map.of("uploadId", "1", "severity", "FATAL"));
        assertThat(byUpload.path("totalElements").asLong()).isEqualTo(1);

        // An inverted date range is a client error, not an empty result.
        mockMvc.perform(get("/api/entries").param("from", "2026-11-24")
                        .param("to", "2026-11-23"))
                .andExpect(status().isBadRequest());
    }

    private JsonNode search(Map<String, String> filters) throws Exception {
        var request = get("/api/entries");
        filters.forEach(request::param);
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
