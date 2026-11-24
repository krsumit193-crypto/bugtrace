package com.bugtrace.service;

import com.bugtrace.parser.ParsedLogLine;

/**
 * A parsed line plus the fingerprint it belongs to (null for INFO/DEBUG and
 * unknown-severity lines).
 */
public record EntryDraft(ParsedLogLine line, String fingerprint) {
}
