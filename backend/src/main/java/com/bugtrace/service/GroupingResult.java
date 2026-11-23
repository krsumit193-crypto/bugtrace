package com.bugtrace.service;

import java.util.List;
import java.util.Map;

/**
 * Result of grouping one upload's parsed lines.
 *
 * @param entries one draft per parsed line, in file order
 * @param groups  fingerprint -> aggregate, in first-seen order
 */
public record GroupingResult(
        List<EntryDraft> entries,
        Map<String, IncidentGroup> groups
) {
}
