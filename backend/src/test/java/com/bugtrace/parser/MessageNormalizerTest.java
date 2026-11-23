package com.bugtrace.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MessageNormalizerTest {

    private final MessageNormalizer normalizer = new MessageNormalizer();

    @Test
    void replacesVolatileValues() {
        String first = "2026-11-23 10:15:23 ERROR Database connection failed for order 5512 "
                + "requestId=abc-123 ip=192.168.1.10 uuid=550e8400-e29b-41d4-a716-446655440000";
        String second = "2026-12-01 11:22:33 ERROR Database connection failed for order 9999 "
                + "requestId=xyz-999 ip=10.0.0.5 uuid=123e4567-e89b-12d3-a456-426614174000";

        assertThat(normalizer.normalize(first)).isEqualTo(normalizer.normalize(second));
    }

    @Test
    void lowercasesAndCollapsesWhitespace() {
        assertThat(normalizer.normalize("  Database   Connection\tFAILED  "))
                .isEqualTo("database connection failed");
    }

    @Test
    void handlesNullAndBlank() {
        assertThat(normalizer.normalize(null)).isEqualTo("<empty>");
        assertThat(normalizer.normalize("   ")).isEqualTo("<empty>");
    }
}
