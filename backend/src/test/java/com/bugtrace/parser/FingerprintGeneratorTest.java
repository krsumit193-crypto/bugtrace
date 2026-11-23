package com.bugtrace.parser;

import com.bugtrace.enums.Severity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintGeneratorTest {

    private final FingerprintGenerator generator = new FingerprintGenerator();

    @Test
    void fingerprintIsStableAndSixtyFourHexCharacters() {
        String first = generator.generate("payment-service", Severity.ERROR, "database connection failed");
        String second = generator.generate("payment-service", Severity.ERROR, "database connection failed");

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void levelIsPartOfTheFingerprint() {
        String warn = generator.generate("payment-service", Severity.WARN, "database connection failed");
        String error = generator.generate("payment-service", Severity.ERROR, "database connection failed");

        assertThat(warn).isNotEqualTo(error);
    }

    @Test
    void serviceIsPartOfTheFingerprintAndIsCaseInsensitive() {
        String first = generator.generate("Payment-Service", Severity.ERROR, "failed");
        String second = generator.generate("payment-service", Severity.ERROR, "failed");
        String other = generator.generate("order-service", Severity.ERROR, "failed");

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(other);
    }

    @Test
    void nullServiceUsesUnknownBucket() {
        String first = generator.generate(null, Severity.ERROR, "failed");
        String second = generator.generate("unknown", Severity.ERROR, "failed");

        assertThat(first).isEqualTo(second);
    }
}
