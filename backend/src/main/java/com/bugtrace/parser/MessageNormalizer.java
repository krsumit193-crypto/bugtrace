package com.bugtrace.parser;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes a log message before it is hashed into a fingerprint.
 *
 * <p>Volatile parts (timestamps, UUIDs, addresses, request ids, numbers) are
 * replaced with stable placeholders. This makes two occurrences of the same
 * bug group together even when the surrounding values differ.</p>
 */
@Component
public class MessageNormalizer {

    private static final Pattern TIMESTAMP = Pattern.compile(
            "\\b\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\b");

    private static final Pattern UUID = Pattern.compile(
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

    private static final Pattern HEX_ADDRESS = Pattern.compile("\\b0x[0-9a-fA-F]+\\b");

    private static final Pattern REQUEST_ID = Pattern.compile(
            "\\b(?:request|trace|span|correlation|req|session)[-_]?id\\s*[=:]\\s*[\\w.\\-]+",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * @return a lowercase, whitespace-collapsed message with volatile values replaced
     */
    public String normalize(String message) {
        if (message == null || message.isBlank()) {
            return "<empty>";
        }

        String result = message;
        result = TIMESTAMP.matcher(result).replaceAll("<timestamp>");
        result = UUID.matcher(result).replaceAll("<uuid>");
        result = HEX_ADDRESS.matcher(result).replaceAll("<hex>");
        result = REQUEST_ID.matcher(result).replaceAll("<request-id>");
        result = IPV4.matcher(result).replaceAll("<ip>");
        result = NUMBER.matcher(result).replaceAll("<num>");
        result = WHITESPACE.matcher(result).replaceAll(" ").strip();
        return result.toLowerCase(Locale.ROOT);
    }
}
