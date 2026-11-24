package com.bugtrace.enums;

/**
 * Log severities supported by BugTrace, ordered from most to least severe.
 * TRACE is mapped to DEBUG by the parser because BugTrace deliberately keeps
 * the five levels used by the dashboard and incident grouping.
 */
public enum Severity {
    FATAL,
    ERROR,
    WARN,
    INFO,
    DEBUG
}
