package com.bugtrace.service;

import com.bugtrace.enums.Severity;
import com.bugtrace.parser.FingerprintGenerator;
import com.bugtrace.parser.MessageNormalizer;
import com.bugtrace.parser.ParsedLogLine;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic grouping: normalize the message, hash it together with the
 * service and level, and collect all occurrences of that fingerprint.
 *
 * <p>No fuzzy matching happens here. Heuristic similarity is only used later
 * for "possibly related" suggestions.</p>
 */
@Component
public class IncidentGrouper {

    private final MessageNormalizer messageNormalizer;
    private final FingerprintGenerator fingerprintGenerator;

    public IncidentGrouper(MessageNormalizer messageNormalizer, FingerprintGenerator fingerprintGenerator) {
        this.messageNormalizer = messageNormalizer;
        this.fingerprintGenerator = fingerprintGenerator;
    }

    public GroupingResult group(List<ParsedLogLine> lines, Set<Severity> incidentSeverities,
                                Instant fallbackTime) {
        List<EntryDraft> drafts = new ArrayList<>(lines.size());
        Map<String, IncidentGroup> groups = new LinkedHashMap<>();

        for (ParsedLogLine line : lines) {
            String fingerprint = null;
            if (line.severity() != null && incidentSeverities.contains(line.severity())) {
                String normalized = messageNormalizer.normalize(line.message());
                fingerprint = fingerprintGenerator.generate(line.serviceName(), line.severity(), normalized);
                IncidentGroup group = groups.get(fingerprint);
                if (group == null) {
                    groups.put(fingerprint, new IncidentGroup(fingerprint, line, fallbackTime));
                } else {
                    group.add(line, fallbackTime);
                }
            }
            drafts.add(new EntryDraft(line, fingerprint));
        }

        return new GroupingResult(List.copyOf(drafts), groups);
    }
}
