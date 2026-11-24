package com.bugtrace.entity;

import com.bugtrace.enums.UploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One uploaded log file and its processing summary.
 */
@Entity
@Table(name = "log_upload")
public class LogUpload {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "upload_uuid", nullable = false, length = 36)
    private String uploadUuid;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UploadStatus status;

    @Column(name = "total_lines", nullable = false)
    private int totalLines;

    @Column(name = "parsed_entries", nullable = false)
    private int parsedEntries;

    @Column(name = "malformed_entries", nullable = false)
    private int malformedEntries;

    @Column(name = "incident_count", nullable = false)
    private int incidentCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public LogUpload() {
    }

    public LogUpload(String uploadUuid, String originalFilename, long fileSizeBytes,
                     UploadStatus status, Instant uploadedAt) {
        this.uploadUuid = uploadUuid;
        this.originalFilename = originalFilename;
        this.fileSizeBytes = fileSizeBytes;
        this.status = status;
        this.uploadedAt = uploadedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUploadUuid() {
        return uploadUuid;
    }

    public void setUploadUuid(String uploadUuid) {
        this.uploadUuid = uploadUuid;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public UploadStatus getStatus() {
        return status;
    }

    public void setStatus(UploadStatus status) {
        this.status = status;
    }

    public int getTotalLines() {
        return totalLines;
    }

    public void setTotalLines(int totalLines) {
        this.totalLines = totalLines;
    }

    public int getParsedEntries() {
        return parsedEntries;
    }

    public void setParsedEntries(int parsedEntries) {
        this.parsedEntries = parsedEntries;
    }

    public int getMalformedEntries() {
        return malformedEntries;
    }

    public void setMalformedEntries(int malformedEntries) {
        this.malformedEntries = malformedEntries;
    }

    public int getIncidentCount() {
        return incidentCount;
    }

    public void setIncidentCount(int incidentCount) {
        this.incidentCount = incidentCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(Instant uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}
