package com.bugtrace.parser;

import com.bugtrace.enums.Severity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Builds the SHA-256 fingerprint that defines an incident.
 *
 * <p>Fingerprint input: {@code service | level | normalizedMessage}. Including
 * the level guarantees that a WARN and an ERROR with the same text never merge.</p>
 */
@Component
public class FingerprintGenerator {

    public String generate(String serviceName, Severity severity, String normalizedMessage) {
        String service = serviceName == null || serviceName.isBlank()
                ? "unknown"
                : serviceName.strip().toLowerCase(Locale.ROOT);
        String level = severity == null ? "unknown" : severity.name();
        String normalized = normalizedMessage == null ? "<empty>" : normalizedMessage;
        return sha256Hex(service + "|" + level + "|" + normalized);
    }

    String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by the JVM specification, so this cannot happen.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
