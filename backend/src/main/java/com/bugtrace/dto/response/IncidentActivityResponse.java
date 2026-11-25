package com.bugtrace.dto.response;

import com.bugtrace.entity.IncidentActivity;
import com.bugtrace.enums.IncidentActivityType;

import java.time.Instant;

public record IncidentActivityResponse(
        Long id,
        IncidentActivityType type,
        String detail,
        Instant createdAt
) {

    public static IncidentActivityResponse from(IncidentActivity activity) {
        return new IncidentActivityResponse(
                activity.getId(),
                activity.getType(),
                activity.getDetail(),
                activity.getCreatedAt()
        );
    }
}
