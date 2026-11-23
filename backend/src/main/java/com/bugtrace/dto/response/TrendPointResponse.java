package com.bugtrace.dto.response;

import java.time.LocalDate;

public record TrendPointResponse(
        LocalDate date,
        long count
) {
}
