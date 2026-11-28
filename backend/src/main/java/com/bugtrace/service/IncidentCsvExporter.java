package com.bugtrace.service;

import com.bugtrace.entity.Incident;

import java.util.List;

/**
 * Renders incidents as RFC 4180 CSV for the download endpoint.
 *
 * <p>Two details matter here:</p>
 * <ul>
 *   <li>Values are quoted only when they contain a comma, quote or line break;
 *       embedded quotes are doubled.</li>
 *   <li>Text taken from uploaded logs is untrusted. A title beginning with
 *       {@code =}, {@code +}, {@code -} or {@code @} is a spreadsheet formula,
 *       so it is prefixed with an apostrophe to stop CSV/formula injection in
 *       Excel, LibreOffice and Google Sheets.</li>
 * </ul>
 *
 * <p>The output uses CRLF line endings and a UTF-8 BOM so Excel opens
 * non-ASCII service names and messages correctly.</p>
 */
public final class IncidentCsvExporter {

    /** UTF-8 byte order mark, written once at the start of the file. */
    static final String BOM = "\uFEFF";

    static final String HEADER =
            "id,fingerprint,title,service,severity,status,assignee,occurrences,first_seen,last_seen";

    private IncidentCsvExporter() {
    }

    /**
     * @param incidents incidents in the order the caller wants them exported
     * @return a complete CSV document, including BOM, header and trailing CRLF
     */
    public static String toCsv(List<Incident> incidents) {
        StringBuilder csv = new StringBuilder(BOM.length() + HEADER.length() + 128
                + incidents.size() * 160);
        csv.append(BOM).append(HEADER).append("\r\n");

        for (Incident incident : incidents) {
            csv.append(incident.getId() == null ? "" : incident.getId()).append(',')
                    .append(escape(incident.getFingerprint())).append(',')
                    .append(escape(incident.getTitle())).append(',')
                    .append(escape(incident.getServiceName())).append(',')
                    .append(escape(incident.getSeverity() == null ? null : incident.getSeverity().name()))
                    .append(',')
                    .append(escape(incident.getStatus() == null ? null : incident.getStatus().name()))
                    .append(',')
                    .append(escape(incident.getAssignee())).append(',')
                    .append(incident.getOccurrenceCount()).append(',')
                    .append(escape(incident.getFirstSeen() == null ? null : incident.getFirstSeen().toString()))
                    .append(',')
                    .append(escape(incident.getLastSeen() == null ? null : incident.getLastSeen().toString()))
                    .append("\r\n");
        }

        return csv.toString();
    }

    /**
     * Escapes one CSV field.
     *
     * @param value raw value; {@code null} becomes an empty field
     * @return a safe field, quoted when required
     */
    static String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String safe = value;
        if (startsWithFormulaCharacter(value)) {
            // Leading apostrophe tells spreadsheets "this is text, not a formula".
            safe = "'" + value;
        }

        boolean needsQuotes = safe.indexOf(',') >= 0
                || safe.indexOf('"') >= 0
                || safe.indexOf('\n') >= 0
                || safe.indexOf('\r') >= 0;

        if (!needsQuotes) {
            return safe;
        }
        return '"' + safe.replace("\"", "\"\"") + '"';
    }

    private static boolean startsWithFormulaCharacter(String value) {
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r';
    }
}
