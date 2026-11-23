package com.bugtrace.parser;

import java.util.List;

/**
 * Result of parsing a whole uploaded file.
 *
 * @param lines           parsed entries, in file order
 * @param totalLines      every line read from the file (blank lines included)
 * @param malformedLines  non-blank lines that could not be parsed and were not continuations
 */
public record ParseResult(
        List<ParsedLogLine> lines,
        int totalLines,
        int malformedLines
) {
}
