package com.bugtrace.dto.response;

public record ServiceErrorCountResponse(
        String serviceName,
        long count
) {
}
