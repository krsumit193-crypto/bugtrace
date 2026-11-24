package com.bugtrace.service;

/**
 * Result of a CSV export request.
 *
 * @param filename  suggested download name, for example
 *                  {@code bugtrace-incidents-20261124.csv}
 * @param content   UTF-8 encoded CSV document (starts with a BOM)
 * @param rowCount  number of incidents actually written
 * @param truncated true when more rows matched than the export limit
 */
public record IncidentExport(String filename, byte[] content, int rowCount, boolean truncated) {
}
