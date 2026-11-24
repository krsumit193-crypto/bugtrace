package com.bugtrace.dto.response;

public record SeverityCountResponse(
        String severity,
        long count
) {
}
