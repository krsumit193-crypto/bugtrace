package com.bugtrace.dto.response;

import com.bugtrace.entity.LogUpload;
import com.bugtrace.enums.UploadStatus;

import java.time.Instant;
import java.util.List;

public record UploadDetailResponse(
        Long id,
        String uploadUuid,
        String originalFilename,
        long fileSizeBytes,
        UploadStatus status,
        int totalLines,
        int parsedEntries,
        int malformedEntries,
        int incidentCount,
        String errorMessage,
        Instant uploadedAt,
        Instant completedAt,
        List<IncidentSummaryResponse> incidents
) {

    public static UploadDetailResponse from(LogUpload upload, List<IncidentSummaryResponse> incidents) {
        return new UploadDetailResponse(
                upload.getId(),
                upload.getUploadUuid(),
                upload.getOriginalFilename(),
                upload.getFileSizeBytes(),
                upload.getStatus(),
                upload.getTotalLines(),
                upload.getParsedEntries(),
                upload.getMalformedEntries(),
                upload.getIncidentCount(),
                upload.getErrorMessage(),
                upload.getUploadedAt(),
                upload.getCompletedAt(),
                incidents
        );
    }
}
