package com.bugtrace.dto.response;

import java.util.List;

/**
 * Everything the overview dashboard needs, in one response. All values come
 * from the database; an empty database produces zeros and empty lists.
 */
public record DashboardStatsResponse(
        long totalUploads,
        long totalLines,
        long parsedEntries,
        long malformedEntries,
        long totalEntries,
        long uniqueIncidents,
        long openIncidents,
        long warnEntries,
        long errorEntries,
        long fatalEntries,
        List<IncidentSummaryResponse> topRepeatedErrors,
        List<ServiceErrorCountResponse> errorsByService,
        List<SeverityCountResponse> severityDistribution,
        List<TrendPointResponse> errorsOverTime,
        List<IncidentSummaryResponse> recentIncidents
) {
}
